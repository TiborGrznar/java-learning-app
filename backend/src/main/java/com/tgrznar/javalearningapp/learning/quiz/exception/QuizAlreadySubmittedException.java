package com.tgrznar.javalearningapp.learning.quiz.exception;

/** Thrown when a quiz token was already used for a submission. A quiz is submitted once, then loaded again. */
public class QuizAlreadySubmittedException extends RuntimeException {

    public QuizAlreadySubmittedException() {
        super("Quiz already submitted");
    }
}