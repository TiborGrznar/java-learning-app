package com.tgrznar.javalearningapp.schoolclass.exception;

/** The student already belongs to a class. */
public class AlreadyEnrolledException extends RuntimeException {

    public AlreadyEnrolledException() {
        super("Student is already enrolled in a class");
    }
}