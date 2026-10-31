package com.tgrznar.javalearningapp.codingtask.dto;

import jakarta.validation.constraints.NotNull;

public record CodingTaskActiveRequest(

        @NotNull(message = "Stav je povinný")
        Boolean active
) {
}
