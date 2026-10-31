package com.tgrznar.javalearningapp.learning.coding;

/** Verdict on one test case. */
public enum CaseVerdict {
    PASSED,
    /** The program ran, but printed a different output. */
    FAILED,
    /** The program crashed, threw an exception or ran out of memory. */
    RUNTIME_ERROR,
    /** The program ran longer than the time limit. */
    TIMEOUT
}
