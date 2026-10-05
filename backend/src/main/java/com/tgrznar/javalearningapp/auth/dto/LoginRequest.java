package com.tgrznar.javalearningapp.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Payload for login (UC-01). */
public record LoginRequest(

        @NotBlank(message = "E-mail je povinný")
        String email,

        @NotBlank(message = "Heslo je povinné")
        String password
) {
}