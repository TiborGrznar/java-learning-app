package com.tgrznar.javalearningapp.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only endpoints used to verify role checks (@PreAuthorize) before real admin endpoints exist.
 * Lives in src/test, so it is never part of the packaged application.
 * Class and methods are public on purpose: method security wraps the bean in a proxy.
 */
@RestController
@RequestMapping("/api/v1/test-roles")
public class RoleTestController {

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminOnly() {
        return "admin";
    }

    @GetMapping("/teacher-or-admin")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public String teacherOrAdmin() {
        return "teacher-or-admin";
    }
}