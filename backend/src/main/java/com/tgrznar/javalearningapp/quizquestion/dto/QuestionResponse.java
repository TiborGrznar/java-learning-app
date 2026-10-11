package com.tgrznar.javalearningapp.quizquestion.dto;

import com.tgrznar.javalearningapp.quizquestion.QuizOption;
import com.tgrznar.javalearningapp.quizquestion.QuizQuestion;

/**
 * Administrator's view of a question. It includes the correct answer, so it must never be
 * returned to students (they get a separate response without it).
 */
public record QuestionResponse(
        Long id,
        Long moduleId,
        String questionText,
        String optionA,
        String optionB,
        String optionC,
        String optionD,
        QuizOption correctOption
) {

    public static QuestionResponse from(QuizQuestion question) {
        return new QuestionResponse(
                question.getId(),
                question.getModule().getId(),
                question.getQuestionText(),
                question.getOptionA(),
                question.getOptionB(),
                question.getOptionC(),
                question.getOptionD(),
                question.getCorrectOption());
    }
}