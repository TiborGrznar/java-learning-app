package com.tgrznar.javalearningapp.user;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** User endpoints. Everything here requires a valid access token (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Profile of the currently logged-in user. The id comes from the verified token
     * (the principal set by JwtAuthenticationFilter), never from the request.
     */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Long userId) {
        return userService.getCurrentUser(userId);
    }
}