package com.tgrznar.javalearningapp.learning.quiz.dto;

import com.tgrznar.javalearningapp.quizquestion.QuizOption;
import jakarta.validation.constraints.NotNull;

/** The student's choice for one question, identified by the stable key of the option. */
public record QuizAnswerRequest(
        @NotNull Long questionId,
        @NotNull QuizOption option
) {
}