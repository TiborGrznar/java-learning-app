package com.tgrznar.javalearningapp.schoolclass.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Full replacement of the editable fields of a class (PUT). The school of a class cannot be changed. */
public record UpdateClassRequest(

        @NotBlank(message = "Názov triedy je povinný")
        @Size(max = 100, message = "Názov triedy môže mať najviac 100 znakov")
        String name,

        @NotNull(message = "Učiteľ je povinný")
        Long teacherId
) {
}