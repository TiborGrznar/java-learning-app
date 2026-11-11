package com.tgrznar.javalearningapp.user.exception;

/** An administrator tried to change their own role, deactivate themselves or reset their own password. */
public class SelfModificationException extends RuntimeException {

    public SelfModificationException() {
        super("Administrators cannot change their own role, deactivate themselves or reset their own password");
    }
}