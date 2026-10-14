package com.tgrznar.javalearningapp.schoolclass.exception;

/** A student who has not joined a class tried to open the learning content. */
public class NotEnrolledException extends RuntimeException {

    public NotEnrolledException() {
        super("Student is not enrolled in a class");
    }
}