package com.tgrznar.javalearningapp.module.dto;

import jakarta.validation.constraints.NotNull;

public record ModuleActiveRequest(

        @NotNull(message = "Stav je povinný")
        Boolean active
) {
}