package com.tgrznar.javalearningapp.auth;

import com.tgrznar.javalearningapp.auth.cookie.RefreshCookieFactory;
import com.tgrznar.javalearningapp.auth.dto.AuthResponse;
import com.tgrznar.javalearningapp.auth.dto.LoginRequest;
import com.tgrznar.javalearningapp.auth.dto.RegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints (see SecurityConfig, the four paths are permitted one by one).
 * The refresh token never appears in a body: login and refresh set it in an HttpOnly cookie, refresh and
 * logout read it from that cookie. Login, refresh and logout are guarded by OriginCheckInterceptor (CSRF).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieFactory cookieFactory;

    /** UC-02: creates a student account. The user then logs in separately. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
    }

    /** UC-01: returns an access token in the body and sets the refresh token cookie. */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return SessionResponses.ok(authService.login(request), cookieFactory);
    }

    /**
     * Exchanges the refresh token cookie for a new access token and a new cookie (the old token is revoked).
     * A missing cookie is 401 INVALID_REFRESH_TOKEN, like an unknown one: this is the normal answer to a
     * visitor who is not logged in. The cookie is deliberately not cleared on failure, because a second tab
     * may have just received a new one.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = RefreshCookieFactory.NAME, required = false) @Nullable String refreshToken) {
        return SessionResponses.ok(authService.refresh(refreshToken), cookieFactory);
    }

    /** Revokes the refresh token of the cookie and deletes the cookie. Always succeeds, also without a cookie. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshCookieFactory.NAME, required = false) @Nullable String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clear().toString())
                .build();
    }
}