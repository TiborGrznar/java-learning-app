package com.tgrznar.javalearningapp.learning.dto;

import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.userprogress.UserProgress;

/** One row of the student's module list. The theory is left out, it is only in the detail. */
public record StudentModuleSummary(
        Long id,
        String title,
        String description,
        int orderNumber,
        ModuleStatus status,
        boolean locked
) {

    /** The progress may be null: a student who has not touched the module has no row yet. */
    public static StudentModuleSummary from(CourseModule module, UserProgress progress, boolean locked) {
        return new StudentModuleSummary(
                module.getId(),
                module.getTitle(),
                module.getDescription(),
                module.getOrderNumber(),
                ModuleStatus.of(progress),
                locked);
    }
}