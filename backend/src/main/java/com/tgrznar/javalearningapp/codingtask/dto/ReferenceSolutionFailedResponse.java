package com.tgrznar.javalearningapp.codingtask.dto;

/**
 * Body of the 409 REFERENCE_SOLUTION_FAILED answer: the usual code and message of an error response plus the
 * diagnostics. Kept apart from {@code ErrorResponse} so the uniform error format does not change.
 */
public record ReferenceSolutionFailedResponse(String code, String message, ReferenceCheckFailure details) {
}
