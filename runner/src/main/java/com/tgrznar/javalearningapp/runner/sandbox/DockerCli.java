package com.tgrznar.javalearningapp.runner.sandbox;

import java.time.Duration;
import java.util.List;

/**
 * The only door to the docker executable. Hidden behind an interface so the orchestration can be
 * tested without Docker.
 */
public interface DockerCli {

    /**
     * Result of a streaming run.
     *
     * @param stdout          captured stdout, cut at {@code maxStdoutBytes}
     * @param stderr          captured stderr, cut at {@code maxStderrBytes}
     * @param exitCode        exit code, or -1 when the process was killed on timeout or output limit
     * @param timedOut        the process was killed because {@code timeout} elapsed
     * @param outputTruncated the process was killed because stdout reached the limit
     */
    record RunOutcome(byte[] stdout, byte[] stderr, int exitCode, boolean timedOut, boolean outputTruncated) {
    }

    /** Result of a short command. */
    record CommandResult(int exitCode, String output) {
    }

    /**
     * Starts the command, writes {@code stdin} to it, and reads its output with hard limits.
     * Never uses a shell. Always returns, killing the process on timeout or when a limit is hit.
     */
    RunOutcome run(List<String> command, byte[] stdin, Duration timeout, int maxStdoutBytes, int maxStderrBytes);

    /** Runs a short docker command and returns its combined output (cut at 64 KiB). */
    CommandResult exec(List<String> command, Duration timeout);
}