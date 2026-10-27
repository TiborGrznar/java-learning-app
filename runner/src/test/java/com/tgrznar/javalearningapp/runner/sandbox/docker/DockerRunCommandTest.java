package com.tgrznar.javalearningapp.runner.sandbox.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.regex.Matcher;

import com.tgrznar.javalearningapp.runner.sandbox.config.SandboxProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DockerRunCommandTest {

    private static final String NAME = "jla-sbx-1730000000000-ab12cd34";
    private static final SandboxProperties PROPS = new SandboxProperties(
            "docker", "jla-sandbox:spike", 10, 5, 65536, 20);

    private static List<String> command() {
        return DockerRunCommand.build(NAME, PROPS);
    }

    /** True when the flag is immediately followed by the value. */
    private static boolean hasPair(List<String> args, String flag, String value) {
        for (int i = 0; i < args.size() - 1; i++) {
            if (args.get(i).equals(flag) && args.get(i + 1).equals(value)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void startsWithDockerRunAndEndsWithTheImage() {
        List<String> args = command();

        assertThat(args.subList(0, 2)).containsExactly("docker", "run");
        assertThat(args.get(args.size() - 1)).isEqualTo("jla-sandbox:spike");
    }

    @Test
    void containsTheIsolationFlagsVerifiedInTheSpike() {
        List<String> args = command();

        assertThat(hasPair(args, "--network", "none")).isTrue();
        assertThat(args).contains("--read-only");
        assertThat(hasPair(args, "--cap-drop", "ALL")).isTrue();
        assertThat(hasPair(args, "--security-opt", "no-new-privileges")).isTrue();
        assertThat(hasPair(args, "--user", "0:0")).isTrue();
        assertThat(hasPair(args, "--memory", "512m")).isTrue();
        assertThat(hasPair(args, "--memory-swap", "512m")).isTrue();
        assertThat(hasPair(args, "--cpus", "1")).isTrue();
        assertThat(hasPair(args, "--pids-limit", "128")).isTrue();
        assertThat(hasPair(args, "--ulimit", "nofile=256:256")).isTrue();
        assertThat(hasPair(args, "--pull", "never")).isTrue();
        assertThat(hasPair(args, "--log-driver", "none")).isTrue();
        assertThat(args).contains("-i", "--rm");
    }

    @Test
    void addsExactlyThreeCapabilities() {
        List<String> args = command();

        long capAdds = args.stream().filter("--cap-add"::equals).count();
        assertThat(capAdds).isEqualTo(3);
        assertThat(hasPair(args, "--cap-add", "SETUID")).isTrue();
        assertThat(hasPair(args, "--cap-add", "SETGID")).isTrue();
        assertThat(hasPair(args, "--cap-add", "KILL")).isTrue();
    }

    @Test
    void mountsOnlyBoundedNoExecTmpfs() {
        List<String> args = command();

        assertThat(hasPair(args, "--tmpfs", "/work:rw,nosuid,nodev,noexec,size=32m,mode=0711")).isTrue();
        assertThat(hasPair(args, "--tmpfs",
                "/build:rw,nosuid,nodev,noexec,size=16m,uid=10003,gid=10003,mode=0755")).isTrue();
        assertThat(hasPair(args, "--tmpfs", "/tmp:rw,nosuid,nodev,noexec,size=16m,mode=1777")).isTrue();
        assertThat(args.stream().filter("--tmpfs"::equals).count()).isEqualTo(3);
    }

    @Test
    void neverSharesTheHostWithTheContainer() {
        List<String> args = command();

        assertThat(args).doesNotContain(
                "-v", "--volume", "--mount", "--privileged", "--pid", "--ipc", "--uts",
                "--device", "--userns", "--cgroupns", "--network=host");
        assertThat(args.stream().anyMatch(a -> a.contains("docker.sock"))).isFalse();
        assertThat(args.stream().filter("--network"::equals).count()).isEqualTo(1);
    }

    @Test
    void passesLimitsAsEnvironmentVariables() {
        List<String> args = command();

        assertThat(hasPair(args, "-e", "COMPILE_TIMEOUT_S=10")).isTrue();
        assertThat(hasPair(args, "-e", "RUN_TIMEOUT_S=5")).isTrue();
        assertThat(hasPair(args, "-e", "MAX_OUTPUT_BYTES=65536")).isTrue();
        assertThat(hasPair(args, "-e", "MAX_CASES=20")).isTrue();
    }

    @Test
    void labelsAndNamesTheContainer() {
        List<String> args = command();

        assertThat(hasPair(args, "--name", NAME)).isTrue();
        assertThat(hasPair(args, "--label", "jla-sandbox=1")).isTrue();
    }

    @Test
    void usesTheConfiguredDockerBinaryAndLimits() {
        SandboxProperties custom = new SandboxProperties(
                "/usr/bin/docker", "jla-sandbox:1", 20, 7, 4096, 5);

        List<String> args = DockerRunCommand.build(NAME, custom);

        assertThat(args.get(0)).isEqualTo("/usr/bin/docker");
        assertThat(args.get(args.size() - 1)).isEqualTo("jla-sandbox:1");
        assertThat(hasPair(args, "-e", "COMPILE_TIMEOUT_S=20")).isTrue();
        assertThat(hasPair(args, "-e", "RUN_TIMEOUT_S=7")).isTrue();
        assertThat(hasPair(args, "-e", "MAX_OUTPUT_BYTES=4096")).isTrue();
        assertThat(hasPair(args, "-e", "MAX_CASES=5")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "x", "jla-sbx-1-abc", "jla-sbx-1-ABCDEF12", "jla-sbx-a-ab12cd34",
            "jla-sbx-1730000000000-ab12cd34 --privileged", "--rm", "jla-sbx-1730000000000-ab12cd34\n"})
    void invalidContainerName_isRejected(String name) {
        assertThatThrownBy(() -> DockerRunCommand.build(name, PROPS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"--privileged", "-v", "Image", "a b", ""})
    void invalidImage_isRejected(String image) {
        SandboxProperties bad = new SandboxProperties("docker", image, 10, 5, 65536, 20);

        assertThatThrownBy(() -> DockerRunCommand.build(NAME, bad))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generatedNames_matchTheFormatAndCarryTheTime() {
        String first = DockerRunCommand.newContainerName(1730000000000L);
        String second = DockerRunCommand.newContainerName(1730000000000L);

        Matcher matcher = DockerRunCommand.CONTAINER_NAME.matcher(first);
        assertThat(matcher.matches()).isTrue();
        assertThat(matcher.group(1)).isEqualTo("1730000000000");
        assertThat(first).isNotEqualTo(second);
        assertThat(DockerRunCommand.build(first, PROPS)).isNotEmpty();
    }
}