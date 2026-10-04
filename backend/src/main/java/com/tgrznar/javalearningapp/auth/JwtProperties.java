package com.tgrznar.javalearningapp.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Type-safe binding of the app.jwt.* settings from application.yaml. */
@ConfigurationProperties(prefix = "app.jwt")
@Validated
public record JwtProperties(

        // Base64 of at least 32 bytes (44 chars with padding), needed for HS256.
        @NotBlank @Size(min = 43)
        String secret,

        @NotNull
        Duration accessTokenTtl,

        @NotNull
        Duration refreshTokenTtl
) {
}