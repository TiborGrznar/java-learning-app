package com.tgrznar.javalearningapp.auth.dto;

import com.tgrznar.javalearningapp.user.model.UserRole;

/**
 * Body returned after login, after every refresh and after a password change. The refresh token is not part
 * of it: it travels only in the HttpOnly cookie (see RefreshCookieFactory), so a script on the page cannot read it.
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UserRole role
) {
}