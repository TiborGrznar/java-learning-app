package com.tgrznar.javalearningapp.user.admin;

import com.tgrznar.javalearningapp.common.PageResponse;
import com.tgrznar.javalearningapp.user.dto.*;
import com.tgrznar.javalearningapp.user.model.UserRole;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** Administrator-only user management (UC-04). The role check covers every method of the class. */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @PostMapping
    public ResponseEntity<CreatedUserResponse> create(@Valid @RequestBody CreateUserRequest request,
                                                      @AuthenticationPrincipal Long adminId) {
        CreatedUserResponse created = adminUserService.createUser(request, adminId);
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.user().id()))
                // The response carries a one-time password, it must not be cached.
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(created);
    }

    @GetMapping
    public PageResponse<UserResponse> list(@RequestParam(required = false) Long schoolId,
                                           @RequestParam(required = false) UserRole role,
                                           @RequestParam(required = false) Boolean active,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return adminUserService.list(schoolId, role, active, page, size);
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) {
        return adminUserService.get(id);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id,
                               @Valid @RequestBody UpdateUserRequest request,
                               @AuthenticationPrincipal Long adminId) {
        return adminUserService.update(id, request, adminId);
    }

    @PatchMapping("/{id}/role")
    public UserResponse changeRole(@PathVariable Long id,
                                   @Valid @RequestBody ChangeRoleRequest request,
                                   @AuthenticationPrincipal Long adminId) {
        return adminUserService.changeRole(id, request, adminId);
    }

    @PatchMapping("/{id}/active")
    public UserResponse setActive(@PathVariable Long id,
                                  @Valid @RequestBody UserActiveRequest request,
                                  @AuthenticationPrincipal Long adminId) {
        return adminUserService.setActive(id, request.active(), adminId);
    }
}