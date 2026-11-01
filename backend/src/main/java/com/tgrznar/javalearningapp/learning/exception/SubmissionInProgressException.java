package com.tgrznar.javalearningapp.learning.exception;

/** The student already has a submission being evaluated; a second one is not accepted until it finishes. */
public class SubmissionInProgressException extends RuntimeException {

    public SubmissionInProgressException() {
        super("A submission of this student is already being evaluated");
    }
}
