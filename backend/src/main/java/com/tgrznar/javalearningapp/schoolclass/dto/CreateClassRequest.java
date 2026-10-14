package com.tgrznar.javalearningapp.schoolclass.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Payload for an administrator creating a class. The join code is generated, never sent by the client. */
public record CreateClassRequest(

        @NotBlank(message = "Názov triedy je povinný")
        @Size(max = 100, message = "Názov triedy môže mať najviac 100 znakov")
        String name,

        @NotNull(message = "Škola je povinná")
        Long schoolId,

        @NotNull(message = "Učiteľ je povinný")
        Long teacherId
) {
}