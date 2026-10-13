package com.tgrznar.javalearningapp.schoolclass.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A student's request to join a class. The code is trimmed and upper-cased by the service. */
public record EnrollmentRequest(
        @NotBlank(message = "Kód triedy je povinný")
        @Size(max = 32, message = "Kód triedy je príliš dlhý")
        String joinCode
) {
}