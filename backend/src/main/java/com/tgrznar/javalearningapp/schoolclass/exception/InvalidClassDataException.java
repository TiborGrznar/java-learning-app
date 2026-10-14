package com.tgrznar.javalearningapp.schoolclass.exception;

/** A business rule on one field of the class form was broken, e.g. the chosen teacher belongs to another school. */
public class InvalidClassDataException extends RuntimeException {

    private final String field;

    public InvalidClassDataException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}