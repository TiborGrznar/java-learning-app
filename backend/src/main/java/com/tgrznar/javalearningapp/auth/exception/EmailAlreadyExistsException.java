package com.tgrznar.javalearningapp.auth.exception;

/** Thrown when registering with an e-mail that already has an account. */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException() {
        super("E-mail je už zaregistrovaný");
    }
}