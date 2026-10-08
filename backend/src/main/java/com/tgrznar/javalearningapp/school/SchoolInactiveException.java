package com.tgrznar.javalearningapp.school;

public class SchoolInactiveException extends RuntimeException {

    public SchoolInactiveException() {
        super("School is inactive");
    }
}