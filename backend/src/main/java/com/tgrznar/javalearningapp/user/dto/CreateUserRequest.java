package com.tgrznar.javalearningapp.user.dto;

import com.tgrznar.javalearningapp.user.model.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Payload for an administrator creating a teacher or another administrator (UC-04). */
public record CreateUserRequest(

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

        @NotNull(message = "Rola je povinná")
        UserRole role,

        /** Required for a teacher, optional for an administrator. */
        Long schoolId
) {
}