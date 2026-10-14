package com.tgrznar.javalearningapp.user.admin;

import com.tgrznar.javalearningapp.user.exception.InvalidUserDataException;
import com.tgrznar.javalearningapp.user.exception.UserNotDeletableException;
import com.tgrznar.javalearningapp.user.exception.UserNotFoundException;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUserDeleteServiceTest extends AdminUserServiceTestBase {

    private static final Long TARGET_ID = 5L;

    @Test
    void deleteStudent_matchingEmail_deletesTheStudent() {
        User student = user(TARGET_ID, UserRole.STUDENT, true, null);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(student));

        service.deleteStudent(TARGET_ID, student.getEmail(), ADMIN_ID);

        verify(userRepository).delete(student);
        verify(userRepository).flush();
    }

    @Test
    void deleteStudent_emailIgnoresCaseAndSurroundingSpaces_deletesTheStudent() {
        User student = user(TARGET_ID, UserRole.STUDENT, true, null);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(student));

        service.deleteStudent(TARGET_ID, "  " + student.getEmail().toUpperCase() + " ", ADMIN_ID);

        verify(userRepository).delete(student);
    }

    @Test
    void deleteStudent_unknownUser_throwsNotFound() {
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> service.deleteStudent(TARGET_ID, "any@example.com", ADMIN_ID));

        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void deleteStudent_teacher_throwsNotDeletableBeforeCheckingTheEmail() {
        User teacher = user(TARGET_ID, UserRole.TEACHER, true, school(1L, true));
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(teacher));

        // The e-mail is wrong on purpose: the role check has to come first.
        assertThrows(UserNotDeletableException.class,
                () -> service.deleteStudent(TARGET_ID, "wrong@example.com", ADMIN_ID));

        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void deleteStudent_administrator_throwsNotDeletable() {
        User admin = user(TARGET_ID, UserRole.ADMIN, true, null);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(admin));

        assertThrows(UserNotDeletableException.class,
                () -> service.deleteStudent(TARGET_ID, admin.getEmail(), ADMIN_ID));

        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void deleteStudent_wrongEmail_throwsValidationErrorOnTheField() {
        User student = user(TARGET_ID, UserRole.STUDENT, true, null);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(student));

        InvalidUserDataException ex = assertThrows(InvalidUserDataException.class,
                () -> service.deleteStudent(TARGET_ID, "someone-else@example.com", ADMIN_ID));

        assertEquals("confirmEmail", ex.getField());
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void deleteStudent_missingEmail_throwsValidationErrorOnTheField() {
        User student = user(TARGET_ID, UserRole.STUDENT, true, null);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(student));

        InvalidUserDataException ex = assertThrows(InvalidUserDataException.class,
                () -> service.deleteStudent(TARGET_ID, null, ADMIN_ID));

        assertEquals("confirmEmail", ex.getField());
        verify(userRepository, never()).delete(any(User.class));
    }
}