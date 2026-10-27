package com.tgrznar.javalearningapp.auth.token;

import com.tgrznar.javalearningapp.auth.exception.InvalidRefreshTokenException;
import com.tgrznar.javalearningapp.auth.security.JwtProperties;
import com.tgrznar.javalearningapp.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Long TOKEN_ID = 10L;

    @Mock
    private RefreshTokenRepository repository;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties("unused-in-this-test", Duration.ofMinutes(15), Duration.ofDays(7));
        service = new RefreshTokenService(repository, properties);
    }

    @Test
    void rotate_activeToken_revokesItOnceAndIssuesOneReplacement() {
        RefreshToken stored = token(false, Instant.now().plus(Duration.ofDays(1)));
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));
        when(repository.revokeIfActive(TOKEN_ID)).thenReturn(1);

        RefreshTokenService.RotatedToken rotated = service.rotate("raw-token");

        assertThat(rotated.user()).isSameAs(stored.getUser());
        assertThat(rotated.refreshToken()).isNotBlank().isNotEqualTo("raw-token");
        assertThat(stored.isRevoked()).isTrue();
        verify(repository).save(any(RefreshToken.class));
    }

    @Test
    void rotate_anotherRequestRevokedTheTokenFirst_throwsAndIssuesNothing() {
        // The token looked active when it was read, but the compare-and-set found it already revoked.
        RefreshToken stored = token(false, Instant.now().plus(Duration.ofDays(1)));
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));
        when(repository.revokeIfActive(TOKEN_ID)).thenReturn(0);

        assertThatThrownBy(() -> service.rotate("raw-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(repository, never()).save(any(RefreshToken.class));
    }

    @Test
    void rotate_unknownToken_throwsWithoutTouchingTheDatabase() {
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("raw-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(repository, never()).revokeIfActive(anyLong());
        verify(repository, never()).save(any(RefreshToken.class));
    }

    @Test
    void rotate_revokedToken_throwsWithoutTouchingTheDatabase() {
        when(repository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(token(true, Instant.now().plus(Duration.ofDays(1)))));

        assertThatThrownBy(() -> service.rotate("raw-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(repository, never()).revokeIfActive(anyLong());
        verify(repository, never()).save(any(RefreshToken.class));
    }

    @Test
    void rotate_expiredToken_throwsWithoutTouchingTheDatabase() {
        when(repository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(token(false, Instant.now().minus(Duration.ofSeconds(1)))));

        assertThatThrownBy(() -> service.rotate("raw-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(repository, never()).revokeIfActive(anyLong());
        verify(repository, never()).save(any(RefreshToken.class));
    }

    private static RefreshToken token(boolean revoked, Instant expiresAt) {
        RefreshToken token = new RefreshToken();
        token.setId(TOKEN_ID);
        token.setUser(new User());
        token.setRevoked(revoked);
        token.setExpiresAt(expiresAt);
        return token;
    }
}