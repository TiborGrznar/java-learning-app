package com.tgrznar.javalearningapp.school.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SchoolRequest(
        @NotBlank(message = "Názov školy je povinný.")
        @Size(max = 255, message = "Názov školy môže mať najviac 255 znakov.")
        String name,

        @NotBlank(message = "Adresa školy je povinná.")
        @Size(max = 255, message = "Adresa školy môže mať najviac 255 znakov.")
        String address
) {
}