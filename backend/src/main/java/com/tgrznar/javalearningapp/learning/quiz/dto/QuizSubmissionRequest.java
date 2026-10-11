package com.tgrznar.javalearningapp.learning.quiz.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record QuizSubmissionRequest(
        @NotBlank String quizToken,
        @NotEmpty @Valid List<QuizAnswerRequest> answers
) {
}