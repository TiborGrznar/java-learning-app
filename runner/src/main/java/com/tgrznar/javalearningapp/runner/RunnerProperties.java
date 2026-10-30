package com.tgrznar.javalearningapp.runner;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the runner (prefix "runner" in application.yaml).
 *
 * @param token shared secret the backend sends in the X-Runner-Token header; the application
 *              refuses to start without one that is at least 32 characters long
 */
@Validated
@ConfigurationProperties(prefix = "runner")
public record RunnerProperties(
        @NotBlank
        @Size(min = 32, message = "must be at least 32 characters long")
        String token) {
}