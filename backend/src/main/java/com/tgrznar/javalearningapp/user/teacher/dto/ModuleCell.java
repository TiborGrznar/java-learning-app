package com.tgrznar.javalearningapp.user.teacher.dto;

import com.tgrznar.javalearningapp.learning.dto.ModuleStatus;

/**
 * One student in one module. A student who has not started the module gets a cell too (NOT_STARTED, all flags
 * false), so every row has exactly one cell per module header, in the same order.
 */
public record ModuleCell(
        Long moduleId,
        ModuleStatus status,
        boolean theoryCompleted,
        boolean quizCompleted,
        boolean codingCompleted
) {
}
