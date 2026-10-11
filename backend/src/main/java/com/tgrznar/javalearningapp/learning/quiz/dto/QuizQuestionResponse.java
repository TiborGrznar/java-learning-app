package com.tgrznar.javalearningapp.learning.quiz.dto;

import java.util.List;

/** A question as the student sees it. It never contains the correct answer. */
public record QuizQuestionResponse(Long id, String text, List<QuizOptionResponse> options) {
}