package com.tgrznar.javalearningapp.school.exception;

public class SchoolInactiveException extends RuntimeException {

    public SchoolInactiveException() {
        super("School is inactive");
    }
}