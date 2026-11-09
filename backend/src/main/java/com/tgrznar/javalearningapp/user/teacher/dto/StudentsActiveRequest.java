package com.tgrznar.javalearningapp.user.teacher.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Deactivation (active = false) or reactivation (active = true) of several students at once (UC-12). The ids may
 * repeat, a repeated id counts once.
 */
public record StudentsActiveRequest(

        @NotEmpty(message = "Vyberte aspoň jedného študenta")
        @Size(max = 100, message = "Naraz možno zmeniť najviac 100 študentov")
        List<@NotNull(message = "Identifikátor študenta je povinný") Long> studentIds,

        @NotNull(message = "Stav účtu je povinný")
        Boolean active
) {
}
