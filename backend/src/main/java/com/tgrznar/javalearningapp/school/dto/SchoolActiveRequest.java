package com.tgrznar.javalearningapp.school.dto;

import jakarta.validation.constraints.NotNull;

public record SchoolActiveRequest(
        @NotNull(message = "Stav školy je povinný.")
        Boolean active
) {
}