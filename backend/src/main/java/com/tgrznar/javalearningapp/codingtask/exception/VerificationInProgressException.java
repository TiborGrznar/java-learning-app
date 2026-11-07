package com.tgrznar.javalearningapp.codingtask.exception;

/** Another reference-solution verification is running; only one runs at a time. */
public class VerificationInProgressException extends RuntimeException {

    public VerificationInProgressException() {
        super("Another reference solution is being verified");
    }
}
