package com.tgrznar.javalearningapp.learning.quiz.dto;

import java.util.List;

/** The outcome of one quiz attempt. The percentage is rounded down. */
public record QuizResultResponse(
        int score,
        int maxScore,
        int percent,
        boolean passed,
        List<QuizAnswerResult> answers
) {
}