package com.tgrznar.javalearningapp.learning.quiz.exception;

/** Thrown when a quiz token is malformed, tampered with, or belongs to another student or module. */
public class InvalidQuizTokenException extends RuntimeException {

    public InvalidQuizTokenException() {
        super("Invalid quiz token");
    }
}