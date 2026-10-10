package com.tgrznar.javalearningapp.auth;

import com.tgrznar.javalearningapp.auth.dto.AuthResponse;
import com.tgrznar.javalearningapp.auth.dto.LoginRequest;
import com.tgrznar.javalearningapp.auth.dto.RefreshRequest;
import com.tgrznar.javalearningapp.auth.dto.RegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Public authentication endpoints (see SecurityConfig, /api/v1/auth/** is permitted). */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** UC-02: creates a student account. The user then logs in separately. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
    }

    /** UC-01: returns an access and refresh token pair. */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** Exchanges a refresh token for a new token pair (the old refresh token is revoked). */
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    /** Revokes the given refresh token. Always succeeds, even for an unknown token. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
    }
}