package com.tgrznar.javalearningapp.codingtask.exception;

/** No coding task with the requested id exists in the requested module. */
public class CodingTaskNotFoundException extends RuntimeException {

    public CodingTaskNotFoundException(Long id) {
        super("Coding task not found: " + id);
    }
}
