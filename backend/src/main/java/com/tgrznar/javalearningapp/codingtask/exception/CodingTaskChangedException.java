package com.tgrznar.javalearningapp.codingtask.exception;

/** The task (its code or its cases) was changed while its reference solution was being verified, so the result no longer applies. */
public class CodingTaskChangedException extends RuntimeException {

    public CodingTaskChangedException(Long id) {
        super("Coding task " + id + " was changed during the verification of its reference solution");
    }
}
