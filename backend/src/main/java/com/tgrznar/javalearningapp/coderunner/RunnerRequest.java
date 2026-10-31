package com.tgrznar.javalearningapp.coderunner;

import java.util.List;

/** Body of {@code POST /run} of the code runner. Field names are the runner's API, do not rename them. */
public record RunnerRequest(String solutionSource, String driverSource, List<String> caseInputs) {

    /** The sources are student and author code: they must never end up in a log line. */
    @Override
    public String toString() {
        return "RunnerRequest[cases=" + (caseInputs == null ? 0 : caseInputs.size()) + "]";
    }
}
