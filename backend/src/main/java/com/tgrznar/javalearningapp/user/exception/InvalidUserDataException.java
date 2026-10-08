package com.tgrznar.javalearningapp.user.exception;

/** A business rule on a single form field was violated; the message is Slovak text for the user. */
public class InvalidUserDataException extends RuntimeException {

    private final String field;

    public InvalidUserDataException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}