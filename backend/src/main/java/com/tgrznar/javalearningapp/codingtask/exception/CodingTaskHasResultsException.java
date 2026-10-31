package com.tgrznar.javalearningapp.codingtask.exception;

/** A coding task that students have already submitted solutions to cannot be deleted, only deactivated. */
public class CodingTaskHasResultsException extends RuntimeException {

    public CodingTaskHasResultsException(Long id) {
        super("Coding task " + id + " has submitted results and cannot be deleted");
    }
}
