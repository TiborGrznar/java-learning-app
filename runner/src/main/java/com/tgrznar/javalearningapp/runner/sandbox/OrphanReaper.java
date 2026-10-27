package com.tgrznar.javalearningapp.runner.sandbox;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Removes sandbox containers that outlived their run (for example after a crash of the runner).
 *
 * <p>At startup it removes every container carrying the sandbox label, which assumes a single
 * runner instance per Docker host. Afterwards it removes the ones older than the TTL. Only names
 * in the runner's own format are ever touched.
 */
@Component
public class OrphanReaper {

    private static final Logger log = LoggerFactory.getLogger(OrphanReaper.class);

    private final DockerCli cli;
    private final SandboxProperties props;
    private final SandboxCapacityProperties capacity;

    public OrphanReaper(DockerCli cli, SandboxProperties props, SandboxCapacityProperties capacity) {
        this.cli = cli;
        this.props = props;
        this.capacity = capacity;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reapAtStartup() {
        reap(System.currentTimeMillis(), true);
    }

    @Scheduled(fixedDelayString = "${runner.sandbox.capacity.reap-interval-seconds:60}",
            initialDelayString = "${runner.sandbox.capacity.reap-interval-seconds:60}",
            timeUnit = TimeUnit.SECONDS)
    public void reapExpired() {
        reap(System.currentTimeMillis(), false);
    }

    /**
     * @param all true removes every labelled container, false only those older than the TTL
     * @return number of containers removed
     */
    int reap(long nowMillis, boolean all) {
        try {
            List<String> doomed = selectDoomed(listNames(), nowMillis, all);
            if (doomed.isEmpty()) {
                return 0;
            }
            List<String> command = new ArrayList<>(List.of(props.dockerBinary(), "rm", "-f"));
            command.addAll(doomed);
            DockerCli.CommandResult result = cli.exec(command, timeout());
            if (result.exitCode() != 0) {
                log.warn("Removing leftover sandbox containers failed (exit {})", result.exitCode());
                return 0;
            }
            log.info("Removed {} leftover sandbox container(s)", doomed.size());
            return doomed.size();
        } catch (RuntimeException e) {
            log.warn("Sandbox cleanup skipped: {}", e.getMessage());
            return 0;
        }
    }

    private List<String> listNames() {
        DockerCli.CommandResult result = cli.exec(List.of(
                props.dockerBinary(), "ps", "-a",
                "--filter", "label=" + DockerRunCommand.LABEL_KEY + "=" + DockerRunCommand.LABEL_VALUE,
                "--format", "{{.Names}}"), timeout());
        if (result.exitCode() != 0) {
            log.warn("Cannot list sandbox containers (exit {})", result.exitCode());
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String line : result.output().split("\\R")) {
            String name = line.trim();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }

    private List<String> selectDoomed(List<String> names, long nowMillis, boolean all) {
        long ttlMillis = TimeUnit.SECONDS.toMillis(capacity.orphanTtlSeconds());
        List<String> doomed = new ArrayList<>();
        for (String name : names) {
            Matcher matcher = DockerRunCommand.CONTAINER_NAME.matcher(name);
            if (!matcher.matches()) {
                continue;   // not ours by name, never touch it
            }
            if (all || isOlderThan(matcher.group(1), nowMillis, ttlMillis)) {
                doomed.add(name);
            }
        }
        return doomed;
    }

    private static boolean isOlderThan(String createdMillis, long nowMillis, long ttlMillis) {
        try {
            return nowMillis - Long.parseLong(createdMillis) > ttlMillis;
        } catch (NumberFormatException e) {
            return true;   // a timestamp we cannot read is not one we generated
        }
    }

    private Duration timeout() {
        return Duration.ofSeconds(capacity.dockerCommandTimeoutSeconds());
    }
}