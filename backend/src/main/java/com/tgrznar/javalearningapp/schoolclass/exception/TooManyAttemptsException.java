package com.tgrznar.javalearningapp.schoolclass.exception;

/** Too many failed enrollment attempts. The caller may try again after {@link #getRetryAfterSeconds()} seconds. */
public class TooManyAttemptsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyAttemptsException(long retryAfterSeconds) {
        super("Too many failed enrollment attempts");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}