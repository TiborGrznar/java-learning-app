package com.tgrznar.javalearningapp.codingtask.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** The complete list of a task's test cases; it replaces the existing cases as a whole. */
public record CodingTaskCasesRequest(

        @NotNull(message = "Zoznam prípadov je povinný")
        @Size(min = 1, max = 20, message = "Úloha musí mať 1 až 20 testovacích prípadov")
        List<@NotNull(message = "Prípad nesmie byť prázdny") @Valid CodingTaskCaseRequest> cases
) {
}
