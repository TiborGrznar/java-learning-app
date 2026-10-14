package com.tgrznar.javalearningapp.user.admin;

import com.tgrznar.javalearningapp.auth.exception.EmailAlreadyExistsException;
import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.school.exception.SchoolInactiveException;
import com.tgrznar.javalearningapp.user.dto.UpdateUserRequest;
import com.tgrznar.javalearningapp.user.dto.UserResponse;
import com.tgrznar.javalearningapp.user.exception.InvalidUserDataException;
import com.tgrznar.javalearningapp.user.exception.TeacherLeadsClassesException;
import com.tgrznar.javalearningapp.user.exception.UserNotFoundException;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminUserUpdateServiceTest extends AdminUserServiceTestBase {

    @Test
    void update_changesFieldsAndNormalizesEmail() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.STUDENT, true, null)));
        when(userRepository.existsByEmailAndIdNot("peter@example.com", 10L)).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = service.update(10L,
                new UpdateUserRequest(" Peter ", " Horvath ", "Peter@Example.COM", null), ADMIN_ID);

        assertThat(response.name()).isEqualTo("Peter");
        assertThat(response.surname()).isEqualTo("Horvath");
        assertThat(response.email()).isEqualTo("peter@example.com");
        assertThat(response.role()).isEqualTo(UserRole.STUDENT);
    }

    @Test
    void update_duplicateEmail_throwsAndDoesNotSave() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.STUDENT, true, null)));
        when(userRepository.existsByEmailAndIdNot("taken@example.com", 10L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(10L,
                new UpdateUserRequest("Peter", "Horvath", "taken@example.com", null), ADMIN_ID))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_keepsCurrentSchoolEvenWhenItWasDeactivated() {
        School inactive = school(5L, false);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.TEACHER, true, inactive)));
        when(userRepository.existsByEmailAndIdNot("new@example.com", 10L)).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = service.update(10L,
                new UpdateUserRequest("Jana", "Novakova", "new@example.com", 5L), ADMIN_ID);

        assertThat(response.schoolId()).isEqualTo(5L);
        verifyNoInteractions(schoolRepository);
        verifyNoInteractions(classRepository);
    }

    @Test
    void update_teacherWithoutSchool_isRejected() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.TEACHER, true, school(5L, true))));

        assertThatThrownBy(() -> service.update(10L,
                new UpdateUserRequest("Jana", "Novakova", "jana@example.com", null), ADMIN_ID))
                .isInstanceOf(InvalidUserDataException.class)
                .extracting(e -> ((InvalidUserDataException) e).getField())
                .isEqualTo("schoolId");

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_toInactiveSchool_isRejected() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.STUDENT, true, null)));
        when(schoolRepository.findById(7L)).thenReturn(Optional.of(school(7L, false)));

        assertThatThrownBy(() -> service.update(10L,
                new UpdateUserRequest("Peter", "Horvath", "peter@example.com", 7L), ADMIN_ID))
                .isInstanceOf(SchoolInactiveException.class);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_unknownUser_throwsNotFound() {
        when(userRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(10L,
                new UpdateUserRequest("Peter", "Horvath", "peter@example.com", null), ADMIN_ID))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void update_uniqueIndexViolation_isTranslatedToEmailAlreadyExists() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.STUDENT, true, null)));
        when(userRepository.existsByEmailAndIdNot("peter@example.com", 10L)).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.update(10L,
                new UpdateUserRequest("Peter", "Horvath", "peter@example.com", null), ADMIN_ID))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    void update_teacherMovedToOtherSchool_whenLeadsActiveClass_isRejected() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.TEACHER, true, school(5L, true))));
        when(schoolRepository.findById(7L)).thenReturn(Optional.of(school(7L, true)));
        when(classRepository.existsByTeacherIdAndActive(10L, true)).thenReturn(true);

        assertThatThrownBy(() -> service.update(10L,
                new UpdateUserRequest("Jana", "Novakova", "jana@example.com", 7L), ADMIN_ID))
                .isInstanceOf(TeacherLeadsClassesException.class);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_teacherMovedToOtherSchool_whenNoActiveClass_isAllowed() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.TEACHER, true, school(5L, true))));
        when(schoolRepository.findById(7L)).thenReturn(Optional.of(school(7L, true)));
        when(classRepository.existsByTeacherIdAndActive(10L, true)).thenReturn(false);
        when(userRepository.existsByEmailAndIdNot("jana@example.com", 10L)).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = service.update(10L,
                new UpdateUserRequest("Jana", "Novakova", "jana@example.com", 7L), ADMIN_ID);

        assertThat(response.schoolId()).isEqualTo(7L);
    }

    @Test
    void update_teacherStaysInSameSchool_doesNotCheckClasses() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.TEACHER, true, school(5L, true))));
        when(userRepository.existsByEmailAndIdNot("jana@example.com", 10L)).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = service.update(10L,
                new UpdateUserRequest("Jana", "Novakova", "jana@example.com", 5L), ADMIN_ID);

        assertThat(response.schoolId()).isEqualTo(5L);
        verifyNoInteractions(classRepository);
    }

    @Test
    void update_studentMovedToOtherSchool_detachesFromClass() {
        User student = user(10L, UserRole.STUDENT, true, school(5L, true));
        student.setSchoolClass(schoolClass(20L));
        when(userRepository.findById(10L)).thenReturn(Optional.of(student));
        when(schoolRepository.findById(7L)).thenReturn(Optional.of(school(7L, true)));
        when(userRepository.existsByEmailAndIdNot("jano10@example.com", 10L)).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = service.update(10L,
                new UpdateUserRequest("Jano", "Novak", "jano10@example.com", 7L), ADMIN_ID);

        assertThat(response.schoolId()).isEqualTo(7L);
        assertThat(response.classId()).isNull();
        verifyNoInteractions(classRepository);
    }

    @Test
    void update_studentStaysInSameSchool_keepsClass() {
        User student = user(10L, UserRole.STUDENT, true, school(5L, true));
        student.setSchoolClass(schoolClass(20L));
        when(userRepository.findById(10L)).thenReturn(Optional.of(student));
        when(userRepository.existsByEmailAndIdNot("jano10@example.com", 10L)).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = service.update(10L,
                new UpdateUserRequest("Jano", "Novak", "jano10@example.com", 5L), ADMIN_ID);

        assertThat(response.classId()).isEqualTo(20L);
    }
}