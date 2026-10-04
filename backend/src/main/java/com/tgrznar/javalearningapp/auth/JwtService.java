package com.tgrznar.javalearningapp.auth;

import com.tgrznar.javalearningapp.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

/** Creates and validates short-lived JWT access tokens (HS256). */
@Service
public class JwtService {

    private static final String ROLE_CLAIM = "role";

    private final JwtProperties properties;
    private final SecretKey signingKey;
    private final JwtParser parser;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        // Fails fast at startup if the secret is not valid Base64 or shorter than 256 bits.
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
        this.parser = Jwts.parser().verifyWith(signingKey).build();
    }

    /** Subject is the user id, the role is stored as a custom claim. */
    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(ROLE_CLAIM, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenTtl())))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Verifies signature and expiration and returns the claims.
     *
     * @throws JwtException if the token is malformed, tampered with or expired
     */
    public Claims parseAccessToken(String token) {
        return parser.parseSignedClaims(token).getPayload();
    }

    public long getAccessTokenTtlSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }
}