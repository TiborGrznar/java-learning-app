package com.tgrznar.javalearningapp.runner.sandbox.docker;

import static org.assertj.core.api.Assertions.assertThat;

import com.tgrznar.javalearningapp.runner.sandbox.config.SandboxCapacityProperties;
import com.tgrznar.javalearningapp.runner.sandbox.config.SandboxProperties;
import com.tgrznar.javalearningapp.runner.sandbox.exception.SandboxUnavailableException;
import org.junit.jupiter.api.Test;

class OrphanReaperTest {

    private static final long NOW = 1_800_000_000_000L;
    private static final long TTL_MILLIS = 300_000L;

    private static final SandboxProperties PROPS = new SandboxProperties(
            "docker", "jla-sandbox:spike", 10, 5, 65536, 20);
    private static final SandboxCapacityProperties CAPACITY = new SandboxCapacityProperties(
            1, 0, 300, 60, 65536, 65536, 1048576, 15, 10);

    private static String name(long createdMillis, String suffix) {
        return "jla-sbx-" + createdMillis + "-" + suffix;
    }

    private static FakeDockerCli cliListing(String... names) {
        FakeDockerCli cli = new FakeDockerCli();
        cli.onExec = command -> command.contains("ps")
                ? new DockerCli.CommandResult(0, String.join("\r\n", names) + "\r\n")
                : new DockerCli.CommandResult(0, "");
        return cli;
    }

    private static OrphanReaper reaper(FakeDockerCli cli) {
        return new OrphanReaper(cli, PROPS, CAPACITY);
    }

    @Test
    void reapAll_removesEveryContainerWithOurName() {
        String a = name(NOW - 1_000, "aaaaaaaa");
        String b = name(NOW - 900_000, "bbbbbbbb");
        FakeDockerCli cli = cliListing(a, b);

        int removed = reaper(cli).reap(NOW, true);

        assertThat(removed).isEqualTo(2);
        assertThat(cli.execCommands).hasSize(2);
        assertThat(cli.execCommands.get(1)).containsExactly("docker", "rm", "-f", a, b);
    }

    @Test
    void reap_listsOnlyContainersWithTheSandboxLabel() {
        FakeDockerCli cli = cliListing();

        reaper(cli).reap(NOW, true);

        assertThat(cli.execCommands.get(0)).containsSequence("--filter", "label=jla-sandbox=1");
    }

    @Test
    void reapExpired_removesOnlyContainersOlderThanTheTtl() {
        String old = name(NOW - TTL_MILLIS - 1, "aaaaaaaa");
        String atLimit = name(NOW - TTL_MILLIS, "bbbbbbbb");
        String fresh = name(NOW - 1_000, "cccccccc");
        FakeDockerCli cli = cliListing(old, atLimit, fresh);

        int removed = reaper(cli).reap(NOW, false);

        assertThat(removed).isEqualTo(1);
        assertThat(cli.execCommands.get(1)).containsExactly("docker", "rm", "-f", old);
    }

    @Test
    void reap_neverTouchesNamesInAnotherFormat() {
        String ours = name(NOW - 900_000, "aaaaaaaa");
        FakeDockerCli cli = cliListing("my-database", "jla-sbx-bad", "jla-sbx-1-ABCDEF12", ours);

        int removed = reaper(cli).reap(NOW, true);

        assertThat(removed).isEqualTo(1);
        assertThat(cli.execCommands.get(1)).containsExactly("docker", "rm", "-f", ours);
    }

    @Test
    void reap_nothingToRemove_doesNotCallRm() {
        FakeDockerCli cli = cliListing(name(NOW - 1_000, "aaaaaaaa"));

        int removed = reaper(cli).reap(NOW, false);

        assertThat(removed).isZero();
        assertThat(cli.execCommands).hasSize(1);
    }

    @Test
    void reap_listingFails_removesNothing() {
        FakeDockerCli cli = new FakeDockerCli();
        cli.onExec = command -> new DockerCli.CommandResult(1, "daemon not running");

        int removed = reaper(cli).reap(NOW, true);

        assertThat(removed).isZero();
        assertThat(cli.execCommands).hasSize(1);
    }

    @Test
    void reap_dockerThrows_isSwallowed() {
        FakeDockerCli cli = new FakeDockerCli();
        cli.onExec = command -> {
            throw new SandboxUnavailableException("docker is gone");
        };

        assertThat(reaper(cli).reap(NOW, true)).isZero();
    }

    @Test
    void reap_removalFails_reportsZero() {
        String old = name(NOW - 900_000, "aaaaaaaa");
        FakeDockerCli cli = new FakeDockerCli();
        cli.onExec = command -> command.contains("ps")
                ? new DockerCli.CommandResult(0, old + "\n")
                : new DockerCli.CommandResult(1, "error");

        assertThat(reaper(cli).reap(NOW, true)).isZero();
    }

    @Test
    void reapExpired_unreadableTimestamp_isTreatedAsExpired() {
        String weird = "jla-sbx-9999999999999999999-ab12cd34";
        FakeDockerCli cli = cliListing(weird);

        assertThat(reaper(cli).reap(NOW, false)).isEqualTo(1);
    }

    @Test
    void effectiveConcurrency_usesTheConfiguredValueOrHalfOfTheCores() {
        assertThat(new SandboxCapacityProperties(3, 0, 300, 60, 65536, 65536, 1048576, 15, 10)
                .effectiveConcurrency()).isEqualTo(3);
        assertThat(new SandboxCapacityProperties(0, 0, 300, 60, 65536, 65536, 1048576, 15, 10)
                .effectiveConcurrency()).isGreaterThanOrEqualTo(1);
    }
}