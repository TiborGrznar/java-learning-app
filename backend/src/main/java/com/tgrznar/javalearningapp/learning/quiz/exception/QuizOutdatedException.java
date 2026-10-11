package com.tgrznar.javalearningapp.learning.quiz.exception;

/** Thrown when a quiz can no longer be submitted: its token expired or one of its questions was deleted. */
public class QuizOutdatedException extends RuntimeException {

    public QuizOutdatedException(String message) {
        super(message);
    }
}