package com.tgrznar.javalearningapp.schoolclass.dto;

import jakarta.validation.constraints.NotNull;

public record ClassActiveRequest(

        @NotNull(message = "Stav triedy je povinný")
        Boolean active
) {
}