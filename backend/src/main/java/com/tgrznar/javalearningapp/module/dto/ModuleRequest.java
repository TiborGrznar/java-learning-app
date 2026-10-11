package com.tgrznar.javalearningapp.module.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Create and update request of a module. The theory is Markdown, stored as sent. */
public record ModuleRequest(

        @NotBlank(message = "Názov je povinný")
        @Size(max = 255, message = "Názov môže mať najviac 255 znakov")
        String title,

        @Size(max = 1000, message = "Popis môže mať najviac 1000 znakov")
        String description,

        @Size(max = 100_000, message = "Teória môže mať najviac 100 000 znakov")
        String theoryContent,

        @NotNull(message = "Poradie je povinné")
        @Min(value = 1, message = "Poradie musí byť aspoň 1")
        @Max(value = 10_000, message = "Poradie môže byť najviac 10 000")
        Integer orderNumber
) {
}