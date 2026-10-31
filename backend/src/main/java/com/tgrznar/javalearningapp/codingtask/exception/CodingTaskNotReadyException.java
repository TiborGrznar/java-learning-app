package com.tgrznar.javalearningapp.codingtask.exception;

/** A coding task cannot be activated (or left active) without a reference solution and at least one test case. */
public class CodingTaskNotReadyException extends RuntimeException {

    public CodingTaskNotReadyException(Long id) {
        super("Coding task " + id + " needs a reference solution and at least one test case");
    }
}
