package com.tgrznar.javalearningapp.auth.token;

import com.tgrznar.javalearningapp.auth.exception.InvalidRefreshTokenException;
import com.tgrznar.javalearningapp.auth.security.JwtProperties;
import com.tgrznar.javalearningapp.user.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Issues, rotates and revokes opaque refresh tokens.
 * The raw token is returned to the client once; only its SHA-256 hash is stored.
 */
@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32; // 256 bits of entropy

    private final SecureRandom secureRandom = new SecureRandom();
    private final RefreshTokenRepository repository;
    private final JwtProperties properties;

    public RefreshTokenService(RefreshTokenRepository repository, JwtProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /** Result of a successful rotation: the owner of the old token and the newly issued raw token. */
    public record RotatedToken(User user, String refreshToken) {
    }

    /** Creates and stores a new refresh token for the user and returns its raw value. */
    @Transactional
    public String issue(User user) {
        String rawToken = generateRawToken();

        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        entity.setTokenHash(hash(rawToken));
        entity.setExpiresAt(Instant.now().plus(properties.refreshTokenTtl()));
        repository.save(entity);

        return rawToken;
    }

    /**
     * Validates the given token, revokes it and issues a replacement (rotation).
     *
     * @throws InvalidRefreshTokenException if the token is unknown, revoked or expired
     */
    @Transactional
    public RotatedToken rotate(String rawToken) {
        RefreshToken current = repository.findByTokenHash(hash(rawToken))
                .filter(token -> !token.isRevoked())
                .filter(token -> token.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(InvalidRefreshTokenException::new);

        current.setRevoked(true);

        User user = current.getUser();
        return new RotatedToken(user, issue(user));
    }

    /** Revokes the token if it exists. Unknown tokens are ignored so logout is idempotent. */
    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> token.setRevoked(true));
    }

    /** Revokes every refresh token of the user, used when the account is deactivated. */
    @Transactional
    public void revokeAllForUser(Long userId) {
        repository.revokeAllByUserId(userId);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 as lowercase hex, always 64 characters, matching the CHAR(64) column. */
    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}