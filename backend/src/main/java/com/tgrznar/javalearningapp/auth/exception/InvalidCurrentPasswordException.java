package com.tgrznar.javalearningapp.auth.exception;

/** Thrown when the current password given in a password change is wrong. */
public class InvalidCurrentPasswordException extends RuntimeException {

    public InvalidCurrentPasswordException() {
        super("Aktuálne heslo nie je správne");
    }
}
