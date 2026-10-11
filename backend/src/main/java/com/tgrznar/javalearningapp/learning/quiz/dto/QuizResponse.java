package com.tgrznar.javalearningapp.learning.quiz.dto;

import java.util.List;

/** A quiz drawn for one attempt. The token has to be sent back together with the answers. */
public record QuizResponse(String quizToken, List<QuizQuestionResponse> questions) {
}