package com.tgrznar.javalearningapp.quizquestion.exception;

/**
 * Thrown when a question does not exist, or does not belong to the module named in the URL.
 */
public class QuestionNotFoundException extends RuntimeException {

    public QuestionNotFoundException(Long id) {
        super("Question not found: " + id);
    }
}