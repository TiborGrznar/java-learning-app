package com.tgrznar.javalearningapp.user.dto;

import jakarta.validation.constraints.NotNull;

public record UserActiveRequest(

        @NotNull(message = "Stav účtu je povinný")
        Boolean active
) {
}