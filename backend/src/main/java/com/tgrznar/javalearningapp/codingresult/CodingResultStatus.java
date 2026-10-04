package com.tgrznar.javalearningapp.codingresult;

/**
 * Outcome of one sandboxed execution of a user's submitted code.
 */
public enum CodingResultStatus {
    PASSED,
    FAILED,
    COMPILE_ERROR,
    RUNTIME_ERROR,
    TIMEOUT
}