package com.tgrznar.javalearningapp.learning.quiz.dto;

import com.tgrznar.javalearningapp.quizquestion.QuizOption;

/** One answer option of a quiz question. The key is stable, the position in the list is random. */
public record QuizOptionResponse(QuizOption key, String text) {
}