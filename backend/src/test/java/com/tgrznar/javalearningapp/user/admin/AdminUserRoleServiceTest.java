package com.tgrznar.javalearningapp.user.admin;

import com.tgrznar.javalearningapp.user.dto.ChangeRoleRequest;
import com.tgrznar.javalearningapp.user.dto.UserResponse;
import com.tgrznar.javalearningapp.user.exception.InvalidUserDataException;
import com.tgrznar.javalearningapp.user.exception.LastAdminException;
import com.tgrznar.javalearningapp.user.exception.SelfModificationException;
import com.tgrznar.javalearningapp.user.exception.TeacherLeadsClassesException;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUserRoleServiceTest extends AdminUserServiceTestBase {

    @Test
    void changeRole_studentToTeacher_assignsSchool() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.STUDENT, true, null)));
        when(schoolRepository.findById(5L)).thenReturn(Optional.of(school(5L, true)));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = service.changeRole(10L, new ChangeRoleRequest(UserRole.TEACHER, 5L), ADMIN_ID);

        assertThat(response.role()).isEqualTo(UserRole.TEACHER);
        assertThat(response.schoolId()).isEqualTo(5L);
    }

    @Test
    void changeRole_sameRole_isNoOp() {
        when(userRepository.findById(10L))
                .thenReturn(Optional.of(user(10L, UserRole.TEACHER, true, school(5L, true))));

        UserResponse response = service.changeRole(10L, new ChangeRoleRequest(UserRole.TEACHER, null), ADMIN_ID);

        assertThat(response.role()).isEqualTo(UserRole.TEACHER);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void changeRole_ownAccount_isRejected() {
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(user(ADMIN_ID, UserRole.ADMIN, true, null)));

        assertThatThrownBy(() -> service.changeRole(ADMIN_ID, new ChangeRoleRequest(UserRole.STUDENT, null), ADMIN_ID))
                .isInstanceOf(SelfModificationException.class);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void changeRole_lastActiveAdmin_isRejected() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.ADMIN, true, null)));
        when(userRepository.countByRoleAndActive(UserRole.ADMIN, true)).thenReturn(1L);

        assertThatThrownBy(() -> service.changeRole(10L, new ChangeRoleRequest(UserRole.STUDENT, null), ADMIN_ID))
                .isInstanceOf(LastAdminException.class);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void changeRole_adminDemoted_whenAnotherActiveAdminExists() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.ADMIN, true, null)));
        when(userRepository.countByRoleAndActive(UserRole.ADMIN, true)).thenReturn(2L);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = service.changeRole(10L, new ChangeRoleRequest(UserRole.STUDENT, null), ADMIN_ID);

        assertThat(response.role()).isEqualTo(UserRole.STUDENT);
    }

    @Test
    void changeRole_inactiveAdminDemoted_doesNotCountAdmins() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.ADMIN, false, null)));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = service.changeRole(10L, new ChangeRoleRequest(UserRole.STUDENT, null), ADMIN_ID);

        assertThat(response.role()).isEqualTo(UserRole.STUDENT);
        verify(userRepository, never()).countByRoleAndActive(any(), anyBoolean());
    }

    @Test
    void changeRole_toTeacherWithoutSchool_isRejected() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.STUDENT, true, null)));

        assertThatThrownBy(() -> service.changeRole(10L, new ChangeRoleRequest(UserRole.TEACHER, null), ADMIN_ID))
                .isInstanceOf(InvalidUserDataException.class)
                .extracting(e -> ((InvalidUserDataException) e).getField())
                .isEqualTo("schoolId");

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void changeRole_teacherDemoted_whenLeadsActiveClass_isRejected() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.TEACHER, true, school(5L, true))));
        when(classRepository.existsByTeacherIdAndActive(10L, true)).thenReturn(true);

        assertThatThrownBy(() -> service.changeRole(10L, new ChangeRoleRequest(UserRole.STUDENT, null), ADMIN_ID))
                .isInstanceOf(TeacherLeadsClassesException.class);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void changeRole_teacherDemoted_whenNoActiveClass_isAllowed() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.TEACHER, true, school(5L, true))));
        when(classRepository.existsByTeacherIdAndActive(10L, true)).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = service.changeRole(10L, new ChangeRoleRequest(UserRole.STUDENT, null), ADMIN_ID);

        assertThat(response.role()).isEqualTo(UserRole.STUDENT);
    }

    @Test
    void changeRole_studentToTeacher_detachesFromClass() {
        User student = user(10L, UserRole.STUDENT, true, school(5L, true));
        student.setSchoolClass(schoolClass(20L));
        when(userRepository.findById(10L)).thenReturn(Optional.of(student));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = service.changeRole(10L, new ChangeRoleRequest(UserRole.TEACHER, null), ADMIN_ID);

        assertThat(response.role()).isEqualTo(UserRole.TEACHER);
        assertThat(response.classId()).isNull();
    }
}