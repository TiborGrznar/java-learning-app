package com.tgrznar.javalearningapp.user.exception;

/** An administrator tried to change their own role or deactivate themselves. */
public class SelfModificationException extends RuntimeException {

    public SelfModificationException() {
        super("Administrators cannot change their own role or deactivate themselves");
    }
}