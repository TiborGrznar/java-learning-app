package com.tgrznar.javalearningapp.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Full replacement of the editable profile fields of a user (PUT). A null schoolId removes the school. */
public record UpdateUserRequest(

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

        /** Required for a teacher. */
        Long schoolId
) {
}