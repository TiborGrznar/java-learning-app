package com.tgrznar.javalearningapp.user.admin;

import com.tgrznar.javalearningapp.ratelimit.LimiterKeys;
import com.tgrznar.javalearningapp.user.dto.PasswordResetResponse;
import com.tgrznar.javalearningapp.user.exception.SelfModificationException;
import com.tgrznar.javalearningapp.user.exception.UserNotFoundException;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminUserResetPasswordServiceTest extends AdminUserServiceTestBase {

    private static final String NEW_HASH = "hash-of-the-temporary-password";

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void resetPassword_setsGeneratedPasswordAndRevokesTokens_forEveryRole(UserRole role) {
        User target = user(10L, role, true, null);
        target.setPasswordHash("old-hash");
        when(userRepository.findById(10L)).thenReturn(Optional.of(target));
        when(passwordGenerator.generate()).thenReturn(TEMP_PASSWORD);
        when(passwordEncoder.encode(TEMP_PASSWORD)).thenReturn(NEW_HASH);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        PasswordResetResponse response = service.resetPassword(10L, ADMIN_ID);

        assertThat(response.temporaryPassword()).isEqualTo(TEMP_PASSWORD);
        assertThat(response.user().id()).isEqualTo(10L);
        assertThat(response.user().role()).isEqualTo(role);
        assertThat(target.getPasswordHash()).isEqualTo(NEW_HASH);

        // The new hash is saved before the old sessions are ended.
        InOrder order = inOrder(userRepository, refreshTokenService);
        order.verify(userRepository).saveAndFlush(target);
        order.verify(refreshTokenService).revokeAllForUser(10L);
    }

    @Test
    void resetPassword_clearsTheFailedLoginAndFailedChangeCountersOfTheUser() {
        User target = user(10L, UserRole.STUDENT, true, null);
        when(userRepository.findById(10L)).thenReturn(Optional.of(target));
        when(passwordGenerator.generate()).thenReturn(TEMP_PASSWORD);
        when(passwordEncoder.encode(TEMP_PASSWORD)).thenReturn(NEW_HASH);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.resetPassword(10L, ADMIN_ID);

        // Same keys as in AuthService: hash of the normalized e-mail, and the user id.
        verify(rateLimiters.loginFailuresPerEmail()).clear(LimiterKeys.hashed("jano10@example.com"));
        verify(rateLimiters.passwordChangeFailuresPerUser()).clear("10");
    }

    @Test
    void resetPassword_deactivatedAccount_isReset_andStaysDeactivated() {
        User target = user(10L, UserRole.STUDENT, false, null);
        when(userRepository.findById(10L)).thenReturn(Optional.of(target));
        when(passwordGenerator.generate()).thenReturn(TEMP_PASSWORD);
        when(passwordEncoder.encode(TEMP_PASSWORD)).thenReturn(NEW_HASH);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        PasswordResetResponse response = service.resetPassword(10L, ADMIN_ID);

        assertThat(response.user().active()).isFalse();
        assertThat(target.getPasswordHash()).isEqualTo(NEW_HASH);
        verify(refreshTokenService).revokeAllForUser(10L);
    }

    @Test
    void resetPassword_ownAccount_isRejectedAndNothingIsGenerated() {
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(user(ADMIN_ID, UserRole.ADMIN, true, null)));

        assertThatThrownBy(() -> service.resetPassword(ADMIN_ID, ADMIN_ID))
                .isInstanceOf(SelfModificationException.class);

        verifyNoInteractions(passwordGenerator, passwordEncoder, refreshTokenService, rateLimiters);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void resetPassword_unknownUser_throwsNotFound() {
        when(userRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword(10L, ADMIN_ID))
                .isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(passwordGenerator, passwordEncoder, refreshTokenService, rateLimiters);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void passwordResetResponse_toStringDoesNotPrintThePassword() {
        PasswordResetResponse response = new PasswordResetResponse(null, TEMP_PASSWORD);

        assertThat(response.toString()).doesNotContain(TEMP_PASSWORD);
    }
}
