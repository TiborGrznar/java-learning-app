package com.tgrznar.javalearningapp.school.exception;

public class SchoolNotFoundException extends RuntimeException {

    public SchoolNotFoundException(Long id) {
        super("School not found: " + id);
    }
}