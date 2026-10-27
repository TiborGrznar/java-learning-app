package com.tgrznar.javalearningapp.runner.sandbox;

import java.time.Duration;
import java.util.List;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Tells whether the Docker daemon answers. The result is cached for a few seconds so that a
 * health probe called often cannot spawn a docker process every time.
 */
@Component
public class DockerHealth {

    private static final Logger log = LoggerFactory.getLogger(DockerHealth.class);

    private static final long CACHE_NANOS = Duration.ofSeconds(5).toNanos();
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final DockerCli cli;
    private final String dockerBinary;
    private final LongSupplier nanoClock;

    private boolean checked;
    private long checkedAt;
    private boolean lastResult;

    @Autowired
    public DockerHealth(DockerCli cli, SandboxProperties props) {
        this(cli, props, System::nanoTime);
    }

    DockerHealth(DockerCli cli, SandboxProperties props, LongSupplier nanoClock) {
        this.cli = cli;
        this.dockerBinary = props.dockerBinary();
        this.nanoClock = nanoClock;
    }

    public synchronized boolean isDockerAvailable() {
        long now = nanoClock.getAsLong();
        if (checked && now - checkedAt < CACHE_NANOS) {
            return lastResult;
        }
        boolean available;
        try {
            available = cli.exec(
                            List.of(dockerBinary, "version", "--format", "{{.Server.Version}}"), TIMEOUT)
                    .exitCode() == 0;
        } catch (RuntimeException e) {
            log.warn("Docker health check failed: {}", e.getMessage());
            available = false;
        }
        checked = true;
        checkedAt = now;
        lastResult = available;
        return available;
    }
}