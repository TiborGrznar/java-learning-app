package com.tgrznar.javalearningapp.runner.sandbox.docker;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;

/** Test double for {@link DockerCli}: records every call and answers with configurable results. */
public final class FakeDockerCli implements DockerCli {

    public final List<List<String>> runCommands = new CopyOnWriteArrayList<>();
    public final List<byte[]> stdins = new CopyOnWriteArrayList<>();
    public final List<Duration> timeouts = new CopyOnWriteArrayList<>();
    public final List<Integer> maxStdouts = new CopyOnWriteArrayList<>();
    public final List<List<String>> execCommands = new CopyOnWriteArrayList<>();

    public volatile Supplier<RunOutcome> onRun = FakeDockerCli::okOutcome;
    public volatile Function<List<String>, CommandResult> onExec = command -> new CommandResult(0, "");

    public static RunOutcome okOutcome() {
        return new RunOutcome("@@JLA 1\n@@DONE\n".getBytes(StandardCharsets.US_ASCII),
                new byte[0], 0, false, false);
    }

    @Override
    public RunOutcome run(List<String> command, byte[] stdin, Duration timeout,
                          int maxStdoutBytes, int maxStderrBytes) {
        runCommands.add(command);
        stdins.add(stdin);
        timeouts.add(timeout);
        maxStdouts.add(maxStdoutBytes);
        return onRun.get();
    }

    @Override
    public CommandResult exec(List<String> command, Duration timeout) {
        execCommands.add(command);
        return onExec.apply(command);
    }
}