package com.tgrznar.javalearningapp.learning.quiz.exception;

/** Thrown when a module has no quiz questions, so there is nothing to solve or evaluate. */
public class QuizNotAvailableException extends RuntimeException {

    public QuizNotAvailableException(Long moduleId) {
        super("Module has no quiz questions: " + moduleId);
    }
}