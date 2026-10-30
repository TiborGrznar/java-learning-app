package com.tgrznar.javalearningapp.runner.sandbox;

import java.util.List;

/**
 * One submission to run. The expected outputs are never part of it: comparing results is the
 * backend's job, the runner only reports what the program printed.
 *
 * @param solutionSource the student's Solution.java
 * @param driverSource   optional Main.java (entry point written by the task author), may be null
 * @param caseInputs     stdin of each case in run order; empty means one run with empty stdin
 */
public record SandboxRequest(String solutionSource, String driverSource, List<String> caseInputs) {

    public SandboxRequest {
        if (solutionSource == null) {
            throw new IllegalArgumentException("solutionSource is required");
        }
        caseInputs = caseInputs == null ? List.of() : List.copyOf(caseInputs);
    }
}