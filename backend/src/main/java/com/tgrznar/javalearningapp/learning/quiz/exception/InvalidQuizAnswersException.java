package com.tgrznar.javalearningapp.learning.quiz.exception;

/** Thrown when the submitted answers do not match the questions the student was shown. */
public class InvalidQuizAnswersException extends RuntimeException {

    public InvalidQuizAnswersException(String message) {
        super(message);
    }
}