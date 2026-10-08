package com.tgrznar.javalearningapp.user;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** Administrator-only user management (UC-04). The role check covers every method of the class. */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserAdminService userAdminService;

    public AdminUserController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @PostMapping
    public ResponseEntity<CreatedUserResponse> create(@Valid @RequestBody CreateUserRequest request,
                                                      @AuthenticationPrincipal Long adminId) {
        CreatedUserResponse created = userAdminService.createUser(request, adminId);
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.user().id()))
                // The response carries a one-time password, it must not be cached.
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(created);
    }
}