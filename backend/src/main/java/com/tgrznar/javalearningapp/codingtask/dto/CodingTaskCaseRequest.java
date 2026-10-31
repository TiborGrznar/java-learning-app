package com.tgrznar.javalearningapp.codingtask.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One test case. Empty strings are valid (a program may read no input or print nothing), null is
 * not. The case number is not sent: it is the position in the list. hidden has no default on
 * purpose, so an expected output is never made visible to students by omission.
 */
public record CodingTaskCaseRequest(

        @NotNull(message = "Vstup je povinný")
        @Size(max = 16_384, message = "Vstup môže mať najviac 16 384 znakov")
        String inputText,

        @NotNull(message = "Očakávaný výstup je povinný")
        @Size(max = 16_384, message = "Očakávaný výstup môže mať najviac 16 384 znakov")
        String expectedOutput,

        @NotNull(message = "Viditeľnosť je povinná")
        Boolean hidden
) {
}
