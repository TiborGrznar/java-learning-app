package com.tgrznar.javalearningapp.runner.sandbox;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs one submission in a throwaway container and returns the raw result frames.
 *
 * <p>Failure model: a problem of the sandbox itself (Docker missing, the launcher refusing to run,
 * a container that never came back, no closing frame) is a {@link SandboxUnavailableException},
 * never a verdict on the student's code. Invalid input (too many cases, oversized files) is an
 * {@link IllegalArgumentException} raised before any slot or container is used.
 *
 * <p>The container is always removed by name in a finally block: killing the docker client does
 * not stop the container, and a leftover would keep consuming memory and CPU. The
 * {@link OrphanReaper} is the second line of defence if even that fails.
 */
@Service
public class DockerSandbox {

    private static final Logger log = LoggerFactory.getLogger(DockerSandbox.class);

    private static final byte[] DONE_FRAME = "@@DONE\n".getBytes(StandardCharsets.US_ASCII);
    private static final int STDERR_LIMIT = 8 * 1024;
    private static final int LOG_MESSAGE_LIMIT = 300;
    /** Space for the frame headers around the capped outputs. */
    private static final int FRAME_OVERHEAD_PER_FRAME = 1024;

    private final DockerCli cli;
    private final SandboxProperties props;
    private final SandboxCapacityProperties capacity;
    private final SandboxLimiter limiter;

    public DockerSandbox(DockerCli cli, SandboxProperties props, SandboxCapacityProperties capacity) {
        this.cli = cli;
        this.props = props;
        this.capacity = capacity;
        this.limiter = new SandboxLimiter(capacity.effectiveConcurrency(), capacity.queue());
    }

    /**
     * @throws IllegalArgumentException    for input over the limits
     * @throws SandboxBusyException        when all slots and the queue are taken
     * @throws SandboxUnavailableException when the sandbox itself failed
     */
    public SandboxResult run(SandboxRequest request) {
        byte[] archive = buildArchive(request);
        int cases = Math.max(1, request.caseInputs().size());
        Duration timeout = Duration.ofSeconds(
                props.compileTimeoutSeconds()
                        + (long) cases * props.runTimeoutSeconds()
                        + capacity.startupSlackSeconds());
        int maxStdout = maxStdoutBytes(cases);

        try {
            limiter.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SandboxUnavailableException("Interrupted while waiting for a sandbox slot", e);
        }
        try {
            return runContainer(archive, timeout, maxStdout);
        } finally {
            limiter.release();
        }
    }

    /**
     * Upper bound of the launcher's stdout: the compiler output and, for every case, stdout and
     * stderr, each capped by the launcher at maxOutputBytes, plus the frame headers.
     */
    int maxStdoutBytes(int cases) {
        long capped = (long) props.maxOutputBytes() * (1 + 2L * cases);
        long frames = (long) FRAME_OVERHEAD_PER_FRAME * (cases + 2);
        return (int) Math.min(capped + frames, Integer.MAX_VALUE);
    }

    private SandboxResult runContainer(byte[] archive, Duration timeout, int maxStdout) {
        String name = DockerRunCommand.newContainerName(System.currentTimeMillis());
        List<String> command = DockerRunCommand.build(name, props);
        DockerCli.RunOutcome outcome;
        try {
            outcome = cli.run(command, archive, timeout, maxStdout, STDERR_LIMIT);
        } finally {
            removeContainer(name);
        }
        return evaluate(name, outcome);
    }

    private SandboxResult evaluate(String name, DockerCli.RunOutcome outcome) {
        if (outcome.timedOut()) {
            throw new SandboxUnavailableException("Container did not finish in time: " + name);
        }
        String stderr = new String(outcome.stderr(), StandardCharsets.UTF_8);
        if (outcome.outputTruncated()) {
            log.warn("Output limit reached, container {} was stopped", name);
            return new SandboxResult(outcome.stdout(), stderr, outcome.exitCode(), true);
        }
        if (outcome.exitCode() != 0) {
            throw new SandboxUnavailableException(
                    "Docker exited with " + outcome.exitCode() + " for " + name + ": " + shorten(stderr));
        }
        if (!endsWith(outcome.stdout(), DONE_FRAME)) {
            throw new SandboxUnavailableException("No closing frame in the output of " + name);
        }
        log.debug("Container {} finished: {}", name, shorten(stderr));
        return new SandboxResult(outcome.stdout(), stderr, 0, false);
    }

    private void removeContainer(String name) {
        try {
            DockerCli.CommandResult result = cli.exec(
                    List.of(props.dockerBinary(), "rm", "-f", name),
                    Duration.ofSeconds(capacity.dockerCommandTimeoutSeconds()));
            if (result.exitCode() != 0 && !result.output().contains("No such container")) {
                log.warn("Could not remove container {}: {}", name, shorten(result.output()));
            }
        } catch (RuntimeException e) {
            // The reaper removes it later; the result of the run must not be lost over this.
            log.warn("Could not remove container {}: {}", name, e.getMessage());
        }
    }

    private byte[] buildArchive(SandboxRequest request) {
        List<String> inputs = request.caseInputs();
        if (inputs.size() > props.maxCases()) {
            throw new IllegalArgumentException("Too many cases");
        }
        List<UstarTarWriter.Entry> entries = new ArrayList<>();
        entries.add(new UstarTarWriter.Entry("Solution.java",
                limited(request.solutionSource(), capacity.maxSourceBytes(), "Solution")));
        if (request.driverSource() != null) {
            entries.add(new UstarTarWriter.Entry("Main.java",
                    limited(request.driverSource(), capacity.maxSourceBytes(), "Driver")));
        }
        for (int i = 0; i < inputs.size(); i++) {
            entries.add(new UstarTarWriter.Entry("cases/" + (i + 1) + ".in",
                    limited(inputs.get(i), capacity.maxCaseInputBytes(), "Case input")));
        }
        return UstarTarWriter.write(entries, capacity.maxArchiveBytes());
    }

    private static byte[] limited(String text, int limit, String what) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > limit) {
            throw new IllegalArgumentException(what + " is too large");
        }
        return bytes;
    }

    private static boolean endsWith(byte[] data, byte[] suffix) {
        if (data.length < suffix.length) {
            return false;
        }
        int offset = data.length - suffix.length;
        for (int i = 0; i < suffix.length; i++) {
            if (data[offset + i] != suffix[i]) {
                return false;
            }
        }
        return true;
    }

    private static String shorten(String text) {
        String single = text.replaceAll("[\\p{Cntrl}]+", " ").trim();
        return single.length() <= LOG_MESSAGE_LIMIT ? single : single.substring(0, LOG_MESSAGE_LIMIT) + "...";
    }
}