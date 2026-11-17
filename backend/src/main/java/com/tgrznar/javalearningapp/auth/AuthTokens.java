package com.tgrznar.javalearningapp.auth;

import com.tgrznar.javalearningapp.auth.dto.AuthResponse;

/**
 * Result of a login, refresh or password change inside the backend: the body for the client and the raw
 * refresh token. The refresh token never goes into the body, the controller puts it into an HttpOnly cookie.
 *
 * @param response     what the client reads (access token, its lifetime, role)
 * @param refreshToken the raw refresh token, to be sent only in the cookie
 */
public record AuthTokens(AuthResponse response, String refreshToken) {

    /** The generated version would print the refresh token. */
    @Override
    public String toString() {
        return "AuthTokens[response=" + response + ", refreshToken=****]";
    }
}
