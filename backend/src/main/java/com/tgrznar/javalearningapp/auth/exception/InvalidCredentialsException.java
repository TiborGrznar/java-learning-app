package com.tgrznar.javalearningapp.auth.exception;

/** Thrown on login when the e-mail is unknown or the password is wrong (deliberately indistinguishable). */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Nesprávne prihlasovacie údaje");
    }
}