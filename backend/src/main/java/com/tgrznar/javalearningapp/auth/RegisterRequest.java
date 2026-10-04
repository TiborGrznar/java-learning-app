package com.tgrznar.javalearningapp.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload for self-service student registration (UC-02). */
public record RegisterRequest(

        @NotBlank @Size(max = 100)
        String name,

        @NotBlank @Size(max = 100)
        String surname,

        @NotBlank @Email @Size(max = 255)
        String email,

        // Max 72 because BCrypt ignores everything beyond 72 bytes.
        @NotBlank @Size(min = 8, max = 72)
        String password,

        @NotBlank
        String confirmPassword
) {
}