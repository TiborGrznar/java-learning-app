package com.tgrznar.javalearningapp.codingtask.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create and update request of a coding task. The active flag is not part of it: it changes only
 * through the dedicated endpoint, which enforces the activation rules. The code fields are stored
 * as sent (indentation can matter); they are only ever executed inside the sandbox.
 */
public record CodingTaskRequest(

        @NotBlank(message = "Názov je povinný")
        @Size(max = 255, message = "Názov môže mať najviac 255 znakov")
        String title,

        @Size(max = 2000, message = "Zadanie môže mať najviac 2000 znakov")
        String description,

        @Size(max = 16_384, message = "Šablóna kódu môže mať najviac 16 384 znakov")
        String templateCode,

        @Size(max = 16_384, message = "Ovládací kód môže mať najviac 16 384 znakov")
        String driverCode,

        @Size(max = 16_384, message = "Referenčné riešenie môže mať najviac 16 384 znakov")
        String referenceSolution,

        @NotNull(message = "Poradie je povinné")
        @Min(value = 1, message = "Poradie musí byť aspoň 1")
        @Max(value = 10_000, message = "Poradie môže byť najviac 10 000")
        Integer orderNumber
) {
}
