package com.tgrznar.javalearningapp.auth.dto;

import com.tgrznar.javalearningapp.common.validation.ValidPassword;
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

        // BCrypt ignores everything beyond 72 bytes, so the limit is counted in bytes (see ValidPassword).
        @NotBlank(message = "Heslo je povinné")
        @ValidPassword
        String password,

        @NotBlank(message = "Potvrdenie hesla je povinné")
        String confirmPassword
) {
}