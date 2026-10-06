package com.tgrznar.javalearningapp.school;

import jakarta.validation.constraints.NotNull;

public record SchoolActiveRequest(
        @NotNull(message = "Stav školy je povinný.")
        Boolean active
) {
}