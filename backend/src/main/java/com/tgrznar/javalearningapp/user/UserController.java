package com.tgrznar.javalearningapp.user;

import com.tgrznar.javalearningapp.auth.AuthService;
import com.tgrznar.javalearningapp.auth.dto.AuthResponse;
import com.tgrznar.javalearningapp.auth.dto.ChangePasswordRequest;
import com.tgrznar.javalearningapp.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** User endpoints. Everything here requires a valid access token (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    /**
     * Profile of the currently logged-in user. The id comes from the verified token
     * (the principal set by JwtAuthenticationFilter), never from the request.
     */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Long userId) {
        return userService.getCurrentUser(userId);
    }

    /**
     * Changes the password of the current user and answers with a new token pair (every older refresh token
     * is revoked). The answer carries tokens, so it must not be cached.
     */
    @PutMapping("/me/password")
    public ResponseEntity<AuthResponse> changePassword(@AuthenticationPrincipal Long userId,
                                                       @Valid @RequestBody ChangePasswordRequest request) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(authService.changePassword(userId, request));
    }
}