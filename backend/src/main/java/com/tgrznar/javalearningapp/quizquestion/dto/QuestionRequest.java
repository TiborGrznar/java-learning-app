package com.tgrznar.javalearningapp.quizquestion.dto;

import com.tgrznar.javalearningapp.quizquestion.QuizOption;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload an administrator sends to create or replace a quiz question.
 * Limits mirror the column lengths in the schema so oversized input fails validation, not the database.
 */
public record QuestionRequest(
        @NotBlank @Size(max = 1000) String questionText,
        @NotBlank @Size(max = 500) String optionA,
        @NotBlank @Size(max = 500) String optionB,
        @NotBlank @Size(max = 500) String optionC,
        @NotBlank @Size(max = 500) String optionD,
        @NotNull QuizOption correctOption
) {
}