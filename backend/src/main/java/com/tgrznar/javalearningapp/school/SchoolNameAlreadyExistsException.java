package com.tgrznar.javalearningapp.school;

public class SchoolNameAlreadyExistsException extends RuntimeException {

    public SchoolNameAlreadyExistsException() {
        super("School name already exists");
    }
}