package com.tgrznar.javalearningapp.runner.sandbox.result;

/**
 * Result of one case.
 *
 * @param number  1-based position of the case, equal to its position in the request
 * @param status  classification of the run
 * @param millis  measured run time
 * @param stdout  program output for {@link CaseStatus#OK}, empty otherwise
 * @param message sanitized text for the student (error description), empty when there is nothing to show
 */
public record CaseResult(int number, CaseStatus status, long millis, String stdout, String message) {
}