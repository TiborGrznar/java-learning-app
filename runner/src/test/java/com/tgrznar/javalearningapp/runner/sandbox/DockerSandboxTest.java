package com.tgrznar.javalearningapp.runner.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DockerSandboxTest {

    private static final SandboxProperties PROPS = new SandboxProperties(
            "docker", "jla-sandbox:spike", 10, 5, 65536, 20);

    private static SandboxCapacityProperties capacity(int concurrency, int queue) {
        return new SandboxCapacityProperties(concurrency, queue, 300, 60, 65536, 65536, 1048576, 15, 10);
    }

    private static SandboxRequest request(String... caseInputs) {
        return new SandboxRequest("class Solution {}", null, List.of(caseInputs));
    }

    private static String nameOf(List<String> runCommand) {
        return runCommand.get(runCommand.indexOf("--name") + 1);
    }

    private static String latin1(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    private static DockerCli.RunOutcome outcome(String stdout, int exit, boolean timedOut, boolean truncated) {
        return new DockerCli.RunOutcome(stdout.getBytes(StandardCharsets.US_ASCII),
                "launcher: boom".getBytes(StandardCharsets.UTF_8), exit, timedOut, truncated);
    }

    @Test
    void run_success_returnsFramesAndRemovesTheContainerByName() {
        FakeDockerCli cli = new FakeDockerCli();
        DockerSandbox sandbox = new DockerSandbox(cli, PROPS, capacity(2, 0));

        SandboxResult result = sandbox.run(request("1", "2"));

        assertThat(new String(result.stdout(), StandardCharsets.US_ASCII)).isEqualTo("@@JLA 1\n@@DONE\n");
        assertThat(result.exitCode()).isZero();
        assertThat(result.outputTruncated()).isFalse();
        assertThat(cli.runCommands).hasSize(1);
        List<String> command = cli.runCommands.get(0);
        assertThat(command).isEqualTo(DockerRunCommand.build(nameOf(command), PROPS));
        assertThat(cli.execCommands).containsExactly(List.of("docker", "rm", "-f", nameOf(command)));
    }

    @Test
    void run_sendsTheSubmissionAsTarOnStdin() {
        FakeDockerCli cli = new FakeDockerCli();
        DockerSandbox sandbox = new DockerSandbox(cli, PROPS, capacity(1, 0));

        sandbox.run(new SandboxRequest("class Solution {}", "class Main {}", List.of("a", "b")));

        String tar = latin1(cli.stdins.get(0));
        assertThat(cli.stdins.get(0).length % 512).isZero();
        assertThat(tar).contains("Solution.java", "Main.java", "cases/1.in", "cases/2.in");
        assertThat(tar).doesNotContain("cases/3.in");
    }

    @Test
    void run_withoutDriverAndCases_sendsOnlyTheSolution() {
        FakeDockerCli cli = new FakeDockerCli();
        DockerSandbox sandbox = new DockerSandbox(cli, PROPS, capacity(1, 0));

        sandbox.run(request());

        String tar = latin1(cli.stdins.get(0));
        assertThat(tar).contains("Solution.java");
        assertThat(tar).doesNotContain("Main.java").doesNotContain("cases/");
    }

    @Test
    void run_derivesTimeoutAndOutputLimitFromTheNumberOfCases() {
        FakeDockerCli cli = new FakeDockerCli();
        DockerSandbox sandbox = new DockerSandbox(cli, PROPS, capacity(1, 0));

        sandbox.run(request("1", "2"));

        // compile 10 s + 2 cases * 5 s + slack 15 s
        assertThat(cli.timeouts.get(0)).isEqualTo(Duration.ofSeconds(35));
        // 65536 * (1 + 2 * 2) + 1024 * (2 + 2)
        assertThat(cli.maxStdouts.get(0)).isEqualTo(331776);
    }

    @Test
    void run_dockerTimeout_throwsUnavailableAndStillRemovesTheContainer() {
        FakeDockerCli cli = new FakeDockerCli();
        cli.onRun = () -> outcome("", -1, true, false);
        DockerSandbox sandbox = new DockerSandbox(cli, PROPS, capacity(1, 0));

        assertThatThrownBy(() -> sandbox.run(request()))
                .isInstanceOf(SandboxUnavailableException.class);
        assertThat(cli.execCommands).hasSize(1);
        assertThat(cli.execCommands.get(0)).containsSequence("docker", "rm", "-f");
    }

    @Test
    void run_dockerCannotStart_propagatesAndStillTriesToRemove() {
        FakeDockerCli cli = new FakeDockerCli();
        cli.onRun = () -> {
            throw new SandboxUnavailableException("Cannot start the docker executable");
        };
        DockerSandbox sandbox = new DockerSandbox(cli, PROPS, capacity(1, 0));

        assertThatThrownBy(() -> sandbox.run(request()))
                .isInstanceOf(SandboxUnavailableException.class);
        assertThat(cli.execCommands).hasSize(1);
    }

    @Test
    void run_nonZeroExit_isAnInfrastructureFault() {
        FakeDockerCli cli = new FakeDockerCli();
        cli.onRun = () -> outcome("@@JLA 1\n@@DONE\n", 125, false, false);
        DockerSandbox sandbox = new DockerSandbox(cli, PROPS, capacity(1, 0));

        assertThatThrownBy(() -> sandbox.run(request()))
                .isInstanceOf(SandboxUnavailableException.class)
                .hasMessageContaining("125");
    }

    @Test
    void run_missingClosingFrame_isAnInfrastructureFault() {
        FakeDockerCli cli = new FakeDockerCli();
        cli.onRun = () -> outcome("@@JLA 1\n@@COMPILE 0 0 0\n\n", 0, false, false);
        DockerSandbox sandbox = new DockerSandbox(cli, PROPS, capacity(1, 0));

        assertThatThrownBy(() -> sandbox.run(request()))
                .isInstanceOf(SandboxUnavailableException.class);
    }

    @Test
    void run_outputLimitReached_returnsTruncatedResultAndRemovesTheContainer() {
        FakeDockerCli cli = new FakeDockerCli();
        cli.onRun = () -> outcome("@@JLA 1\n@@CASE 1", -1, false, true);
        DockerSandbox sandbox = new DockerSandbox(cli, PROPS, capacity(1, 0));

        SandboxResult result = sandbox.run(request());

        assertThat(result.outputTruncated()).isTrue();
        assertThat(cli.execCommands).hasSize(1);
    }

    @Test
    void run_removalFailure_doesNotLoseTheResult() {
        FakeDockerCli cli = new FakeDockerCli();
        cli.onExec = command -> {
            throw new SandboxUnavailableException("docker is gone");
        };
        DockerSandbox sandbox = new DockerSandbox(cli, PROPS, capacity(1, 0));

        SandboxResult result = sandbox.run(request());

        assertThat(result.exitCode()).isZero();
    }

    @Test
    void run_tooManyCases_isRejectedBeforeAnyContainerIsStarted() {
        FakeDockerCli cli = new FakeDockerCli();
        SandboxProperties fewCases = new SandboxProperties("docker", "jla-sandbox:spike", 10, 5, 65536, 2);
        DockerSandbox sandbox = new DockerSandbox(cli, fewCases, capacity(1, 0));

        assertThatThrownBy(() -> sandbox.run(request("1", "2", "3")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(cli.runCommands).isEmpty();
        assertThat(cli.execCommands).isEmpty();
    }

    @Test
    void run_oversizedSourceOrInput_isRejectedBeforeAnyContainerIsStarted() {
        FakeDockerCli cli = new FakeDockerCli();
        DockerSandbox sandbox = new DockerSandbox(cli, PROPS, capacity(1, 0));
        String big = "x".repeat(65537);

        assertThatThrownBy(() -> sandbox.run(new SandboxRequest(big, null, List.of())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sandbox.run(new SandboxRequest("class Solution {}", null, List.of(big))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(cli.runCommands).isEmpty();
    }

    @Test
    void run_afterAFailure_theSlotIsFreeAgain() {
        FakeDockerCli cli = new FakeDockerCli();
        cli.onRun = () -> outcome("", -1, true, false);
        DockerSandbox sandbox = new DockerSandbox(cli, PROPS, capacity(1, 0));
        assertThatThrownBy(() -> sandbox.run(request())).isInstanceOf(SandboxUnavailableException.class);

        cli.onRun = FakeDockerCli::okOutcome;
        SandboxResult result = sandbox.run(request());

        assertThat(result.exitCode()).isZero();
    }

    @Test
    void run_whenAllSlotsAreTaken_isRejectedAsBusy() throws Exception {
        FakeDockerCli cli = new FakeDockerCli();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        cli.onRun = () -> {
            entered.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return FakeDockerCli.okOutcome();
        };
        DockerSandbox sandbox = new DockerSandbox(cli, PROPS, capacity(1, 0));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<SandboxResult> first = executor.submit(() -> sandbox.run(request()));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> sandbox.run(request())).isInstanceOf(SandboxBusyException.class);

            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).exitCode()).isZero();
        } finally {
            executor.shutdownNow();
        }
    }
}