package com.tgrznar.javalearningapp.runner.run;

import com.tgrznar.javalearningapp.runner.sandbox.SandboxRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Body of POST /run. It carries no expected outputs: comparing results is the backend's job.
 * The byte limits of the sources and inputs are enforced by the sandbox (they are configurable),
 * the checks here only reject requests that are malformed. The ceiling of 20 cases is the maximum
 * allowed for runner.sandbox.max-cases; the sandbox applies the configured value.
 *
 * @param solutionSource the student's Solution.java
 * @param driverSource   optional Main.java written by the task author, may be null
 * @param caseInputs     stdin of each case in run order; null or empty means one run with empty stdin
 */
public record RunRequest(
        @NotBlank String solutionSource,
        String driverSource,
        @Size(max = 20) List<@NotNull String> caseInputs) {

    public SandboxRequest toSandboxRequest() {
        return new SandboxRequest(solutionSource, driverSource, caseInputs);
    }
}
