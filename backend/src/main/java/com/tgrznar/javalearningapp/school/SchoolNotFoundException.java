package com.tgrznar.javalearningapp.school;

public class SchoolNotFoundException extends RuntimeException {

    public SchoolNotFoundException(Long id) {
        super("School not found: " + id);
    }
}