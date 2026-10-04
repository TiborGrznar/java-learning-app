package com.tgrznar.javalearningapp.auth;

import com.tgrznar.javalearningapp.user.UserRole;

/** Token pair returned after login and after every refresh. */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        UserRole role
) {
}