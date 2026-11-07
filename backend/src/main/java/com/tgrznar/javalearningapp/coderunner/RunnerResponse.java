package com.tgrznar.javalearningapp.coderunner;

import java.util.List;

/**
 * Answer of the code runner. Field names are the runner's API, do not rename them.
 * <p>
 * {@code stdout} is filled only for status {@link CaseStatus#OK}. All texts are already cleaned and
 * shortened by the runner, but they still come from student code: show them only to the student who
 * wrote it, and never for a hidden case.
 */
public record RunnerResponse(Compile compile, List<CaseResult> cases) {

    /** Result of the compilation. When it did not succeed, {@code message} holds the compiler output and there are no cases. */
    public record Compile(boolean success, boolean timedOut, String message) {
    }

    /** Result of one run of the program. {@code number} starts at 1. */
    public record CaseResult(int number, CaseStatus status, long millis, String stdout, String message) {
    }

    /** How a single run ended. An unknown value from the runner is treated as a broken answer. */
    public enum CaseStatus {
        OK,
        RUNTIME_ERROR,
        TIMEOUT,
        OUT_OF_MEMORY
    }
}
