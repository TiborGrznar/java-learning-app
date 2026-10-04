package com.tgrznar.javalearningapp.auth;

import jakarta.validation.constraints.NotBlank;

/** Payload carrying a raw refresh token; used by both refresh and logout. */
public record RefreshRequest(

        @NotBlank
        String refreshToken
) {
}