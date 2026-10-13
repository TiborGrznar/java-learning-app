package com.tgrznar.javalearningapp.user.admin;

import com.tgrznar.javalearningapp.user.dto.UserResponse;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminUserActiveServiceTest extends AdminUserServiceTestBase {

    @Test
    void setActive_deactivate_revokesAllRefreshTokens() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.STUDENT, true, null)));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = service.setActive(10L, false, ADMIN_ID);

        assertThat(response.active()).isFalse();
        verify(refreshTokenService).revokeAllForUser(10L);
    }

    @Test
    void setActive_reactivate_doesNotRevokeTokens() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.STUDENT, false, null)));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = service.setActive(10L, true, ADMIN_ID);

        assertThat(response.active()).isTrue();
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void setActive_alreadyInRequestedState_isNoOp() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.STUDENT, false, null)));

        UserResponse response = service.setActive(10L, false, ADMIN_ID);

        assertThat(response.active()).isFalse();
        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void setActive_ownAccount_isRejected() {
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(user(ADMIN_ID, UserRole.ADMIN, true, null)));

        assertThatThrownBy(() -> service.setActive(ADMIN_ID, false, ADMIN_ID))
                .isInstanceOf(SelfModificationException.class);

        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void setActive_lastActiveAdmin_isRejected() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.ADMIN, true, null)));
        when(userRepository.countByRoleAndActive(UserRole.ADMIN, true)).thenReturn(1L);

        assertThatThrownBy(() -> service.setActive(10L, false, ADMIN_ID))
                .isInstanceOf(LastAdminException.class);

        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void setActive_adminDeactivated_whenAnotherActiveAdminExists() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.ADMIN, true, null)));
        when(userRepository.countByRoleAndActive(UserRole.ADMIN, true)).thenReturn(2L);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = service.setActive(10L, false, ADMIN_ID);

        assertThat(response.active()).isFalse();
        verify(refreshTokenService).revokeAllForUser(10L);
    }

    @Test
    void setActive_teacherLeadingActiveClass_isRejected() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.TEACHER, true, school(5L, true))));
        when(classRepository.existsByTeacherIdAndActive(10L, true)).thenReturn(true);

        assertThatThrownBy(() -> service.setActive(10L, false, ADMIN_ID))
                .isInstanceOf(TeacherLeadsClassesException.class);

        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void setActive_teacherWithoutActiveClass_isDeactivated() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.TEACHER, true, school(5L, true))));
        when(classRepository.existsByTeacherIdAndActive(10L, true)).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = service.setActive(10L, false, ADMIN_ID);

        assertThat(response.active()).isFalse();
        verify(refreshTokenService).revokeAllForUser(10L);
    }
}