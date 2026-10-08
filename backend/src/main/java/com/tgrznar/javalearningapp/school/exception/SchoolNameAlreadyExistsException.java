package com.tgrznar.javalearningapp.school.exception;

public class SchoolNameAlreadyExistsException extends RuntimeException {

    public SchoolNameAlreadyExistsException() {
        super("School name already exists");
    }
}