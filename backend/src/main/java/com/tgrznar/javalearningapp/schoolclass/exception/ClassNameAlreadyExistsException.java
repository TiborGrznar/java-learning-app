package com.tgrznar.javalearningapp.schoolclass.exception;

/** The school already has a class with this name (compared ignoring letter case). */
public class ClassNameAlreadyExistsException extends RuntimeException {

    public ClassNameAlreadyExistsException() {
        super("Class name already exists in the school");
    }
}