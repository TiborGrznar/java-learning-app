package com.tgrznar.javalearningapp.codingtask.exception;

import com.tgrznar.javalearningapp.codingtask.dto.ReferenceCheckFailure;

/**
 * The reference solution of a task did not pass its own cases, so the task cannot be activated. Carries the
 * diagnostics for the administrator; they hold code output, so the message of the exception itself does not.
 */
public class ReferenceSolutionFailedException extends RuntimeException {

    private final transient ReferenceCheckFailure failure;

    public ReferenceSolutionFailedException(Long taskId, ReferenceCheckFailure failure) {
        super("The reference solution of coding task " + taskId + " failed the verification: " + failure.status());
        this.failure = failure;
    }

    public ReferenceCheckFailure getFailure() {
        return failure;
    }
}
