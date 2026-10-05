package com.tgrznar.javalearningapp.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload for self-service student registration (UC-02). */
public record RegisterRequest(

        @NotBlank(message = "Meno je povinné")
        @Size(max = 100, message = "Meno môže mať najviac 100 znakov")
        String name,

        @NotBlank(message = "Priezvisko je povinné")
        @Size(max = 100, message = "Priezvisko môže mať najviac 100 znakov")
        String surname,

        @NotBlank(message = "E-mail je povinný")
        @Email(message = "E-mail nemá platný formát")
        @Size(max = 255, message = "E-mail môže mať najviac 255 znakov")
        String email,

        // Max 72 because BCrypt ignores everything beyond 72 bytes.
        @NotBlank(message = "Heslo je povinné")
        @Size(min = 8, max = 72, message = "Heslo musí mať 8 až 72 znakov")
        String password,

        @NotBlank(message = "Potvrdenie hesla je povinné")
        String confirmPassword
) {
}