package com.tgrznar.javalearningapp.auth;

import com.tgrznar.javalearningapp.auth.cookie.RefreshCookieFactory;
import com.tgrznar.javalearningapp.auth.dto.AuthResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/**
 * Builds the answer of every endpoint that starts or renews a session (login, refresh, password change):
 * the access token in the body, the refresh token only in the Set-Cookie header. The answer carries
 * tokens, so it must not be cached.
 */
public final class SessionResponses {

    private SessionResponses() {
    }

    public static ResponseEntity<AuthResponse> ok(AuthTokens tokens, RefreshCookieFactory cookies) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.SET_COOKIE, cookies.issue(tokens.refreshToken()).toString())
                .body(tokens.response());
    }
}
