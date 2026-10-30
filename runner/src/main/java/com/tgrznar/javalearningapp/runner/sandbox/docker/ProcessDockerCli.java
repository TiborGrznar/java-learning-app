package com.tgrznar.javalearningapp.runner.sandbox.docker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.tgrznar.javalearningapp.runner.sandbox.exception.SandboxUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs docker through {@link ProcessBuilder} with an argument array, never through a shell.
 * The child gets a minimal environment: only variables the docker client needs to find its daemon
 * (so the runner's own secrets, such as the token, never reach it).
 */
@Component
public class ProcessDockerCli implements DockerCli {

    private static final Logger log = LoggerFactory.getLogger(ProcessDockerCli.class);

    /** Variables copied to the docker process when present. */
    static final Set<String> ENV_WHITELIST = Set.of(
            "PATH", "DOCKER_HOST", "DOCKER_CONTEXT", "DOCKER_CONFIG",
            "HOME", "USERPROFILE", "SystemRoot", "APPDATA");

    private static final int EXEC_OUTPUT_LIMIT = 64 * 1024;

    @Override
    public RunOutcome run(List<String> command, byte[] stdin, Duration timeout,
                          int maxStdoutBytes, int maxStderrBytes) {
        Process process = start(command);
        CompletableFuture<LimitedBytes> out = CompletableFuture.supplyAsync(
                () -> readLimited(process.getInputStream(), maxStdoutBytes, process));
        CompletableFuture<LimitedBytes> err = CompletableFuture.supplyAsync(
                () -> readLimited(process.getErrorStream(), maxStderrBytes, null));
        CompletableFuture.runAsync(() -> writeAndClose(process.getOutputStream(), stdin));

        boolean finished = false;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (!finished) {
            process.destroyForcibly();
            waitQuietly(process);
        }
        LimitedBytes stdout = join(out);
        LimitedBytes stderr = join(err);
        boolean killed = !finished || stdout.truncated();
        int exit = killed ? -1 : process.exitValue();
        return new RunOutcome(stdout.bytes(), stderr.bytes(), exit, !finished && !stdout.truncated(), stdout.truncated());
    }

    @Override
    public CommandResult exec(List<String> command, Duration timeout) {
        Process process = start(command);
        CompletableFuture<LimitedBytes> out = CompletableFuture.supplyAsync(
                () -> readLimited(process.getInputStream(), EXEC_OUTPUT_LIMIT, null));
        CompletableFuture<LimitedBytes> err = CompletableFuture.supplyAsync(
                () -> readLimited(process.getErrorStream(), EXEC_OUTPUT_LIMIT, null));
        closeQuietly(process.getOutputStream());

        boolean finished = false;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (!finished) {
            process.destroyForcibly();
            waitQuietly(process);
        }
        String text = new String(join(out).bytes(), java.nio.charset.StandardCharsets.UTF_8)
                + new String(join(err).bytes(), java.nio.charset.StandardCharsets.UTF_8);
        return new CommandResult(finished ? process.exitValue() : -1, text);
    }

    private static Process start(List<String> command) {
        ProcessBuilder builder = new ProcessBuilder(command);
        Map<String, String> env = builder.environment();
        Map<String, String> inherited = Map.copyOf(System.getenv());
        env.clear();
        for (String name : ENV_WHITELIST) {
            String value = inherited.get(name);
            if (value != null) {
                env.put(name, value);
            }
        }
        try {
            return builder.start();
        } catch (IOException e) {
            throw new SandboxUnavailableException("Cannot start the docker executable", e);
        }
    }

    private record LimitedBytes(byte[] bytes, boolean truncated) {
    }

    /** Reads up to {@code limit} bytes; on overflow it kills {@code toKill} (if given) and stops. */
    private static LimitedBytes readLimited(InputStream in, int limit, Process toKill) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        boolean truncated = false;
        try {
            int n;
            while ((n = in.read(chunk)) != -1) {
                int room = limit - buffer.size();
                if (n > room) {
                    buffer.write(chunk, 0, Math.max(room, 0));
                    truncated = true;
                    if (toKill != null) {
                        toKill.destroyForcibly();
                    }
                    break;
                }
                buffer.write(chunk, 0, n);
            }
        } catch (IOException e) {
            log.debug("Stream closed while reading docker output");
        }
        return new LimitedBytes(buffer.toByteArray(), truncated);
    }

    private static void writeAndClose(OutputStream out, byte[] data) {
        try (out) {
            out.write(data);
        } catch (IOException e) {
            // The container may exit before reading everything (for example when the launcher
            // refuses the input); that is not an error of this method.
            log.debug("Docker stdin closed early");
        }
    }

    private static void closeQuietly(OutputStream out) {
        try {
            out.close();
        } catch (IOException e) {
            log.debug("Docker stdin already closed");
        }
    }

    private static void waitQuietly(Process process) {
        try {
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static LimitedBytes join(CompletableFuture<LimitedBytes> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new LimitedBytes(new byte[0], false);
        } catch (Exception e) {
            return new LimitedBytes(new byte[0], false);
        }
    }
}