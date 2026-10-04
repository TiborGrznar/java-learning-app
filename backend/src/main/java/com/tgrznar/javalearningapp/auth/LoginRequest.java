package com.tgrznar.javalearningapp.auth;

import jakarta.validation.constraints.NotBlank;

/** Payload for login (UC-01). */
public record LoginRequest(

        @NotBlank
        String email,

        @NotBlank
        String password
) {
}