package com.tgrznar.javalearningapp.learning.quiz.exception;

/** Thrown when a student asks for or submits a quiz before marking the module's theory as read. */
public class TheoryNotCompletedException extends RuntimeException {

    public TheoryNotCompletedException(Long moduleId) {
        super("Theory not completed in module: " + moduleId);
    }
}