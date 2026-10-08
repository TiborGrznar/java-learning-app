package com.tgrznar.javalearningapp.auth;

import com.tgrznar.javalearningapp.auth.dto.AuthResponse;
import com.tgrznar.javalearningapp.auth.dto.LoginRequest;
import com.tgrznar.javalearningapp.auth.dto.RefreshRequest;
import com.tgrznar.javalearningapp.auth.dto.RegisterRequest;
import com.tgrznar.javalearningapp.auth.exception.AccountDisabledException;
import com.tgrznar.javalearningapp.auth.exception.EmailAlreadyExistsException;
import com.tgrznar.javalearningapp.auth.exception.InvalidCredentialsException;
import com.tgrznar.javalearningapp.auth.exception.PasswordMismatchException;
import com.tgrznar.javalearningapp.auth.security.JwtService;
import com.tgrznar.javalearningapp.auth.token.RefreshTokenService;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String PASSWORD = "heslo12345";

    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;

    // Real encoder with a low cost factor: fast, and verifies real hashing behaviour.
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService, refreshTokenService);
    }

    // ---------- register ----------

    @Test
    void register_createsStudentWithHashedPasswordAndNormalizedEmail() {
        authService.register(new RegisterRequest("  Jano ", " Novák ", "  Jano@Example.COM ", PASSWORD, PASSWORD));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getName()).isEqualTo("Jano");
        assertThat(saved.getSurname()).isEqualTo("Novák");
        assertThat(saved.getEmail()).isEqualTo("jano@example.com");
        assertThat(saved.getRole()).isEqualTo(UserRole.STUDENT);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, saved.getPasswordHash())).isTrue();
    }

    @Test
    void register_rejectsMismatchedPasswords() {
        assertThatThrownBy(() ->
                authService.register(new RegisterRequest("Jano", "Novák", "jano@example.com", PASSWORD, "ine-heslo")))
                .isInstanceOf(PasswordMismatchException.class);

        verifyNoInteractions(userRepository);
    }

    @Test
    void register_rejectsExistingEmail() {
        when(userRepository.existsByEmail("jano@example.com")).thenReturn(true);

        assertThatThrownBy(() ->
                authService.register(new RegisterRequest("Jano", "Novák", "Jano@Example.com", PASSWORD, PASSWORD)))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void register_translatesConcurrentDuplicateIntoEmailAlreadyExists() {
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() ->
                authService.register(new RegisterRequest("Jano", "Novák", "jano@example.com", PASSWORD, PASSWORD)))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    // ---------- login ----------

    @Test
    void login_returnsTokensAndRoleForValidCredentials() {
        User user = activeUser();
        when(userRepository.findByEmail("jano@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);
        when(refreshTokenService.issue(user)).thenReturn("refresh-token");

        AuthResponse response = authService.login(new LoginRequest("  Jano@Example.com ", PASSWORD));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(900L);
        assertThat(response.role()).isEqualTo(UserRole.STUDENT);
    }

    @Test
    void login_rejectsUnknownEmail() {
        when(userRepository.findByEmail("nikto@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nikto@example.com", PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void login_rejectsWrongPassword() {
        when(userRepository.findByEmail("jano@example.com")).thenReturn(Optional.of(activeUser()));

        assertThatThrownBy(() -> authService.login(new LoginRequest("jano@example.com", "zle-heslo")))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void login_rejectsDisabledAccountAfterCorrectPassword() {
        User user = activeUser();
        user.setActive(false);
        when(userRepository.findByEmail("jano@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("jano@example.com", PASSWORD)))
                .isInstanceOf(AccountDisabledException.class);

        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void login_doesNotRevealDisabledAccountWhenPasswordIsWrong() {
        User user = activeUser();
        user.setActive(false);
        when(userRepository.findByEmail("jano@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("jano@example.com", "zle-heslo")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // ---------- refresh and logout ----------

    @Test
    void refresh_returnsNewTokenPair() {
        User user = activeUser();
        when(refreshTokenService.rotate("old-refresh")).thenReturn(new RefreshTokenService.RotatedToken(user, "new-refresh"));
        when(jwtService.generateAccessToken(user)).thenReturn("new-access");
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);

        AuthResponse response = authService.refresh(new RefreshRequest("old-refresh"));

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        assertThat(response.role()).isEqualTo(UserRole.STUDENT);
    }

    @Test
    void refresh_rejectsDisabledAccountAndIssuesNoAccessToken() {
        User user = activeUser();
        user.setActive(false);
        when(refreshTokenService.rotate("old-refresh")).thenReturn(new RefreshTokenService.RotatedToken(user, "new-refresh"));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("old-refresh")))
                .isInstanceOf(AccountDisabledException.class);

        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    void logout_revokesTheGivenRefreshToken() {
        authService.logout(new RefreshRequest("some-refresh"));

        verify(refreshTokenService).revoke("some-refresh");
    }

    private User activeUser() {
        User user = new User();
        user.setId(1L);
        user.setName("Jano");
        user.setSurname("Novák");
        user.setEmail("jano@example.com");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(UserRole.STUDENT);
        user.setActive(true);
        return user;
    }
}