package com.tgrznar.javalearningapp.schoolclass.exception;

/** The student's school (set by an administrator) differs from the school of the class. */
public class SchoolMismatchException extends RuntimeException {

    public SchoolMismatchException() {
        super("Student's school differs from the school of the class");
    }
}