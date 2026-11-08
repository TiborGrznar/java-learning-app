package com.tgrznar.javalearningapp.learning.progress.dto;

import com.tgrznar.javalearningapp.learning.dto.ModuleStatus;

/**
 * One module in the student's progress overview (UC-10).
 *
 * @param lastQuiz the latest quiz attempt; null when the student has not tried the quiz
 * @param coding   the coding tasks; null until the module is unlocked and its theory read, because a student
 *                 sees the tasks of a module only from then on
 */
public record ModuleProgress(
        Long moduleId,
        String title,
        int orderNumber,
        ModuleStatus status,
        boolean locked,
        boolean theoryCompleted,
        boolean quizCompleted,
        boolean codingCompleted,
        LastQuizResult lastQuiz,
        CodingProgress coding
) {
}
