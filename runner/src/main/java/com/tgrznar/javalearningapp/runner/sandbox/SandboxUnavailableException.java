package com.tgrznar.javalearningapp.runner.sandbox;

/**
 * The sandbox itself failed (Docker missing or erroring, launcher self-check failed, no result
 * frames). It is an infrastructure fault, never a verdict on the student's code. The message is
 * for logs only and must not be sent to the student.
 */
public class SandboxUnavailableException extends RuntimeException {

    public SandboxUnavailableException(String message) {
        super(message);
    }

    public SandboxUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}