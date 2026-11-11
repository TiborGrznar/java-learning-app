package com.tgrznar.javalearningapp.auth;

import com.tgrznar.javalearningapp.auth.dto.AuthResponse;
import com.tgrznar.javalearningapp.auth.dto.ChangePasswordRequest;
import com.tgrznar.javalearningapp.auth.exception.AccountDisabledException;
import com.tgrznar.javalearningapp.auth.exception.InvalidAccessTokenException;
import com.tgrznar.javalearningapp.auth.exception.InvalidCurrentPasswordException;
import com.tgrznar.javalearningapp.auth.exception.PasswordMismatchException;
import com.tgrznar.javalearningapp.auth.exception.PasswordUnchangedException;
import com.tgrznar.javalearningapp.auth.security.JwtService;
import com.tgrznar.javalearningapp.auth.token.RefreshTokenService;
import com.tgrznar.javalearningapp.ratelimit.TestRateLimiters;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit tests of {@link AuthService#changePassword}. The limiter is covered by an API test with a real limit. */
@ExtendWith(MockitoExtension.class)
class AuthServiceChangePasswordTest {

    private static final Long USER_ID = 5L;
    private static final String CURRENT = "stare-heslo-1";
    private static final String NEW = "nove-heslo-22";

    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;

    // Real encoder with a low cost factor: fast, and verifies real hashing behaviour.
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private AuthService authService;
    private User user;
    private String originalHash;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService, refreshTokenService,
                TestRateLimiters.unlimited());

        user = new User();
        user.setId(USER_ID);
        user.setName("Jano");
        user.setSurname("Novak");
        user.setEmail("jano@example.com");
        user.setRole(UserRole.STUDENT);
        user.setActive(true);
        originalHash = passwordEncoder.encode(CURRENT);
        user.setPasswordHash(originalHash);
    }

    @Test
    void changePassword_savesNewHash_revokesEveryTokenAndIssuesANewPair() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);
        when(refreshTokenService.issue(user)).thenReturn("new-refresh-token");

        AuthResponse response = authService.changePassword(USER_ID, request(CURRENT, NEW, NEW));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
        assertThat(response.role()).isEqualTo(UserRole.STUDENT);
        assertThat(user.getPasswordHash()).isNotEqualTo(NEW).isNotEqualTo(originalHash);
        assertThat(passwordEncoder.matches(NEW, user.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(CURRENT, user.getPasswordHash())).isFalse();

        // The new hash is written before the old tokens are revoked, and the new token is issued last.
        InOrder order = inOrder(userRepository, refreshTokenService);
        order.verify(userRepository).saveAndFlush(user);
        order.verify(refreshTokenService).revokeAllForUser(USER_ID);
        order.verify(refreshTokenService).issue(user);
    }

    @Test
    void changePassword_rejectsDifferentConfirmationBeforeTouchingTheDatabase() {
        assertThatThrownBy(() -> authService.changePassword(USER_ID, request(CURRENT, NEW, "ine-heslo-333")))
                .isInstanceOf(PasswordMismatchException.class);

        verifyNoInteractions(userRepository, refreshTokenService);
    }

    @Test
    void changePassword_rejectsUnknownUser() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.changePassword(USER_ID, request(CURRENT, NEW, NEW)))
                .isInstanceOf(InvalidAccessTokenException.class);

        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void changePassword_rejectsDeactivatedAccount() {
        user.setActive(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.changePassword(USER_ID, request(CURRENT, NEW, NEW)))
                .isInstanceOf(AccountDisabledException.class);

        assertThat(user.getPasswordHash()).isEqualTo(originalHash);
        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void changePassword_rejectsWrongCurrentPasswordAndChangesNothing() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.changePassword(USER_ID, request("zle-heslo-9", NEW, NEW)))
                .isInstanceOf(InvalidCurrentPasswordException.class);

        assertThat(user.getPasswordHash()).isEqualTo(originalHash);
        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void changePassword_treatsCurrentPasswordOver72BytesAsWrong() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        // 40 characters, 80 bytes: BCrypt cannot have hashed it in full
        String tooLong = "á".repeat(40);

        assertThatThrownBy(() -> authService.changePassword(USER_ID, request(tooLong, NEW, NEW)))
                .isInstanceOf(InvalidCurrentPasswordException.class);

        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void changePassword_rejectsNewPasswordEqualToTheCurrentOne() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.changePassword(USER_ID, request(CURRENT, CURRENT, CURRENT)))
                .isInstanceOf(PasswordUnchangedException.class);

        assertThat(user.getPasswordHash()).isEqualTo(originalHash);
        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void changePasswordRequest_toStringDoesNotPrintThePasswords() {
        assertThat(request(CURRENT, NEW, NEW).toString()).doesNotContain(CURRENT, NEW);
    }

    private static ChangePasswordRequest request(String current, String newPassword, String confirmation) {
        return new ChangePasswordRequest(current, newPassword, confirmation);
    }
}
