package com.tgrznar.javalearningapp.auth.exception;

/**
 * Thrown when a structurally valid access token refers to a user that no longer exists.
 * The token is useless in that case, so the client has to log in again.
 */
public class InvalidAccessTokenException extends RuntimeException {

    public InvalidAccessTokenException() {
        super("Neplatný prístupový token");
    }
}