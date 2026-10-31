package com.tgrznar.javalearningapp.coderunner;

import java.util.List;

/**
 * The backend's view of the code runner service: runs untrusted code in a sandbox and reports what
 * it printed. The runner never sees expected outputs, comparing is the backend's job.
 * <p>
 * Student code never runs inside this JVM. Callers must not hold a database transaction while
 * calling {@link #run}: one call can last more than a minute.
 */
public interface CodeRunner {

    /**
     * Compiles {@code solutionSource} (and {@code driverSource} when present) and runs the program once
     * for every entry of {@code caseInputs}, with that entry as standard input.
     * <p>
     * The returned answer is checked: when the compilation succeeded it holds exactly one result per
     * input, numbered 1..n in order; when it failed it holds no results.
     *
     * @param driverSource optional {@code Main.java} written by the task author, may be null
     * @param caseInputs   standard input of every case, at least one entry
     * @throws CodeRunnerBusyException        the runner has no free slot, retry later
     * @throws CodeRunnerUnavailableException the runner cannot be reached or answered something unusable
     */
    RunnerResponse run(String solutionSource, String driverSource, List<String> caseInputs);
}
