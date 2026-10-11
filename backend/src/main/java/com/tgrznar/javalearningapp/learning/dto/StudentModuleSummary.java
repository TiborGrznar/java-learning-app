package com.tgrznar.javalearningapp.learning.dto;

import com.tgrznar.javalearningapp.module.CourseModule;

/** One row of the student's module list. The theory is left out, it is only in the detail. */
public record StudentModuleSummary(
        Long id,
        String title,
        String description,
        int orderNumber,
        ModuleStatus status
) {

    public static StudentModuleSummary from(CourseModule module, ModuleStatus status) {
        return new StudentModuleSummary(
                module.getId(),
                module.getTitle(),
                module.getDescription(),
                module.getOrderNumber(),
                status);
    }
}