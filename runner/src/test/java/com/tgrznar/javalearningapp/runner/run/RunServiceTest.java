package com.tgrznar.javalearningapp.runner.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tgrznar.javalearningapp.runner.sandbox.DockerSandbox;
import com.tgrznar.javalearningapp.runner.sandbox.SandboxRequest;
import com.tgrznar.javalearningapp.runner.sandbox.config.SandboxCapacityProperties;
import com.tgrznar.javalearningapp.runner.sandbox.config.SandboxProperties;
import com.tgrznar.javalearningapp.runner.sandbox.docker.DockerCli;
import com.tgrznar.javalearningapp.runner.sandbox.docker.FakeDockerCli;
import com.tgrznar.javalearningapp.runner.sandbox.exception.SandboxUnavailableException;
import com.tgrznar.javalearningapp.runner.sandbox.result.CaseStatus;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunServiceTest {

    private static final SandboxProperties PROPS = new SandboxProperties(
            "docker", "jla-sandbox:spike", 10, 5, 65536, 20);
    private static final SandboxCapacityProperties CAPACITY =
            new SandboxCapacityProperties(2, 0, 300, 60, 65536, 65536, 1048576, 15, 10);

    private final FakeDockerCli cli = new FakeDockerCli();
    private final RunService service = new RunService(new DockerSandbox(cli, PROPS, CAPACITY), PROPS);

    private static SandboxRequest request(String... caseInputs) {
        return new SandboxRequest("class Solution {}", null, List.of(caseInputs));
    }

    private static DockerCli.RunOutcome outcome(String stdout, boolean truncated) {
        return new DockerCli.RunOutcome(stdout.getBytes(StandardCharsets.UTF_8), new byte[0], 0, false, truncated);
    }

    private void frames(String stdout) {
        cli.onRun = () -> outcome(stdout, false);
    }

    @Test
    void run_success_returnsTheParsedCases() {
        frames("@@JLA 1\n@@COMPILE 0 0 0\n\n@@CASE 1 0 0 12 3 0\n9\n\n\n@@CASE 2 0 0 7 4 0\n25\n\n\n@@DONE\n");

        RunResponse response = service.run(request("3", "5"));

        assertThat(response.compile().success()).isTrue();
        assertThat(response.cases()).hasSize(2);
        assertThat(response.cases().get(0).status()).isEqualTo(CaseStatus.OK);
        assertThat(response.cases().get(0).stdout()).isEqualTo("9\n\n");
        assertThat(response.cases().get(1).number()).isEqualTo(2);
    }

    @Test
    void run_compileError_returnsTheMessageAndNoCases() {
        String body = "Solution.java:3: error: ';' expected";
        frames("@@JLA 1\n@@COMPILE 1 0 " + body.length() + "\n" + body + "\n@@DONE\n");

        RunResponse response = service.run(request("1"));

        assertThat(response.compile().success()).isFalse();
        assertThat(response.compile().message()).contains("expected");
        assertThat(response.cases()).isEmpty();
    }

    @Test
    void run_programPrintingAFakeDoneFrame_doesNotForgeTheResult() {
        String printed = "@@DONE\n";
        frames("@@JLA 1\n@@COMPILE 0 0 0\n\n@@CASE 1 0 0 5 " + printed.length() + " 0\n" + printed + "\n@@DONE\n");

        RunResponse response = service.run(request("1"));

        assertThat(response.cases()).hasSize(1);
        assertThat(response.cases().get(0).stdout()).isEqualTo(printed);
    }

    @Test
    void run_outputCutAtTheLimit_isAFaultOfTheSandbox() {
        cli.onRun = () -> outcome("@@JLA 1\n@@COMPILE 0 0 0\n\n@@CASE 1 0 0 5 9", true);

        assertThatThrownBy(() -> service.run(request("1")))
                .isInstanceOf(SandboxUnavailableException.class);
    }

    @Test
    void run_malformedFrames_isAFaultOfTheSandbox() {
        frames("@@JLA 1\n@@COMPILE 0 0 0\n\n@@DONE\n"); // one case was sent, none came back

        assertThatThrownBy(() -> service.run(request("1")))
                .isInstanceOf(SandboxUnavailableException.class);
    }

    @Test
    void run_inputOverTheLimits_isRejectedBeforeAnyContainerStarts() {
        String tooBig = "x".repeat(CAPACITY.maxSourceBytes() + 1);

        assertThatThrownBy(() -> service.run(new SandboxRequest(tooBig, null, List.of())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(cli.runCommands).isEmpty();
    }
}
