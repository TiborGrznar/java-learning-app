package com.tgrznar.javalearningapp.learning.dto;

import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.userprogress.UserProgress;

/** A module with its theory and the student's progress. It never contains the quiz answers. */
public record StudentModuleDetail(
        Long id,
        String title,
        String description,
        String theoryContent,
        int orderNumber,
        ModuleStatus status,
        boolean theoryCompleted,
        boolean quizCompleted,
        boolean codingCompleted,
        long quizQuestionCount
) {

    /** The progress may be null: a student who has not touched the module has no row yet. */
    public static StudentModuleDetail from(CourseModule module, UserProgress progress, long quizQuestionCount) {
        return new StudentModuleDetail(
                module.getId(),
                module.getTitle(),
                module.getDescription(),
                module.getTheoryContent(),
                module.getOrderNumber(),
                ModuleStatus.of(progress),
                progress != null && progress.isTheoryCompleted(),
                progress != null && progress.isQuizCompleted(),
                progress != null && progress.isCodingCompleted(),
                quizQuestionCount);
    }
}