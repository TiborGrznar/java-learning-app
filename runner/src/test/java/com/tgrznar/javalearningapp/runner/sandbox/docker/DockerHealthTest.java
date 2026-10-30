package com.tgrznar.javalearningapp.runner.sandbox.docker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;

import com.tgrznar.javalearningapp.runner.sandbox.config.SandboxProperties;
import com.tgrznar.javalearningapp.runner.sandbox.exception.SandboxUnavailableException;
import org.junit.jupiter.api.Test;

class DockerHealthTest {

    private static final SandboxProperties PROPS = new SandboxProperties(
            "docker", "jla-sandbox:spike", 10, 5, 65536, 20);
    private static final long SECOND = 1_000_000_000L;

    @Test
    void available_whenDockerVersionSucceeds() {
        FakeDockerCli cli = new FakeDockerCli();
        AtomicLong clock = new AtomicLong();
        DockerHealth health = new DockerHealth(cli, PROPS, clock::get);

        assertThat(health.isDockerAvailable()).isTrue();
        assertThat(cli.execCommands.get(0)).containsSequence("docker", "version");
    }

    @Test
    void unavailable_whenDockerVersionFails() {
        FakeDockerCli cli = new FakeDockerCli();
        cli.onExec = command -> new DockerCli.CommandResult(1, "Cannot connect to the Docker daemon");
        DockerHealth health = new DockerHealth(cli, PROPS, new AtomicLong()::get);

        assertThat(health.isDockerAvailable()).isFalse();
    }

    @Test
    void unavailable_whenTheDockerExecutableCannotBeStarted() {
        FakeDockerCli cli = new FakeDockerCli();
        cli.onExec = command -> {
            throw new SandboxUnavailableException("Cannot start the docker executable");
        };
        DockerHealth health = new DockerHealth(cli, PROPS, new AtomicLong()::get);

        assertThat(health.isDockerAvailable()).isFalse();
    }

    @Test
    void result_isCachedForFiveSeconds() {
        FakeDockerCli cli = new FakeDockerCli();
        AtomicLong clock = new AtomicLong();
        DockerHealth health = new DockerHealth(cli, PROPS, clock::get);

        health.isDockerAvailable();
        clock.addAndGet(4 * SECOND);
        health.isDockerAvailable();

        assertThat(cli.execCommands).hasSize(1);

        clock.addAndGet(2 * SECOND);
        health.isDockerAvailable();

        assertThat(cli.execCommands).hasSize(2);
    }

    @Test
    void recovery_isNoticedAfterTheCacheExpires() {
        FakeDockerCli cli = new FakeDockerCli();
        cli.onExec = command -> new DockerCli.CommandResult(1, "down");
        AtomicLong clock = new AtomicLong();
        DockerHealth health = new DockerHealth(cli, PROPS, clock::get);
        assertThat(health.isDockerAvailable()).isFalse();

        cli.onExec = command -> new DockerCli.CommandResult(0, "27.0");
        clock.addAndGet(6 * SECOND);

        assertThat(health.isDockerAvailable()).isTrue();
    }
}