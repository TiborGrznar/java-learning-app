package com.tgrznar.javalearningapp.auth.dto;

import com.tgrznar.javalearningapp.user.model.UserRole;

/** Token pair returned after login and after every refresh. */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        UserRole role
) {
}