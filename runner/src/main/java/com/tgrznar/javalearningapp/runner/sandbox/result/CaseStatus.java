package com.tgrznar.javalearningapp.runner.sandbox.result;

/** Outcome of running one case. The backend maps it to its own result status. */
public enum CaseStatus {
    /** Exit code 0; the stdout is available for comparison. */
    OK,
    /** The program ended with an exception or a non-zero exit code. */
    RUNTIME_ERROR,
    /** The time limit of the case was exceeded. */
    TIMEOUT,
    /** The JVM ran out of memory. */
    OUT_OF_MEMORY
}