package com.tgrznar.javalearningapp.schoolclass.exception;

/** No active class with an active school has this join code. Unknown and inactive codes are not told apart. */
public class InvalidJoinCodeException extends RuntimeException {

    public InvalidJoinCodeException() {
        super("Invalid join code");
    }
}