package com.tgrznar.javalearningapp.learning.quiz.dto;

import com.tgrznar.javalearningapp.quizquestion.QuizOption;

/** The evaluation of one answer, shown after the submission so the student can learn from mistakes. */
public record QuizAnswerResult(
        Long questionId,
        QuizOption selected,
        QuizOption correctOption,
        boolean correct
) {
}