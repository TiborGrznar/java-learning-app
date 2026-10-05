package com.tgrznar.javalearningapp.auth.exception;

/** Thrown when password and confirmPassword differ during registration. */
public class PasswordMismatchException extends RuntimeException {

    public PasswordMismatchException() {
        super("Heslá sa nezhodujú");
    }
}