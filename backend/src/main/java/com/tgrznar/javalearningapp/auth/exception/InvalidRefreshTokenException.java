package com.tgrznar.javalearningapp.auth.exception;

/** Thrown when a refresh token is unknown, expired or already revoked. */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Platnosť prihlásenia vypršala, prihláste sa znova");
    }
}