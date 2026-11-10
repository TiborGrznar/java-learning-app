package com.tgrznar.javalearningapp.auth.dto;

import com.tgrznar.javalearningapp.common.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload for changing the password of the logged-in user. */
public record ChangePasswordRequest(

        @NotBlank(message = "Aktuálne heslo je povinné")
        @Size(max = 1000, message = "Aktuálne heslo je príliš dlhé")
        String currentPassword,

        @NotBlank(message = "Nové heslo je povinné")
        @ValidPassword
        String newPassword,

        @NotBlank(message = "Potvrdenie nového hesla je povinné")
        String confirmNewPassword
) {

    /** A record prints every component, which would put the passwords into a log. */
    @Override
    public String toString() {
        return "ChangePasswordRequest[****]";
    }
}
