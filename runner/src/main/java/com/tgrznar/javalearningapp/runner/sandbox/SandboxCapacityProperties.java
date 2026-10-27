package com.tgrznar.javalearningapp.runner.sandbox;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Capacity and input limits of the sandbox (prefix "runner.sandbox.capacity").
 *
 * @param concurrency          containers running at once; 0 means half of the CPU cores (at least 1)
 * @param queue                requests allowed to wait for a slot; more are rejected with 503
 * @param orphanTtlSeconds     age after which a leftover container is removed by the reaper; must be
 *                             well above the longest legitimate run
 * @param reapIntervalSeconds  pause between two reaper passes
 * @param maxSourceBytes       limit of Solution.java and of Main.java
 * @param maxCaseInputBytes    limit of the stdin of one case
 * @param maxArchiveBytes      limit of the summed content sent to the container
 * @param startupSlackSeconds  allowance for starting the container and the JVM, added to the time
 *                             limit of the whole run
 * @param dockerCommandTimeoutSeconds time limit of short docker commands (rm, ps, version)
 */
@Validated
@ConfigurationProperties(prefix = "runner.sandbox.capacity")
public record SandboxCapacityProperties(
        @DefaultValue("0") @Min(0) @Max(64)
        int concurrency,

        @DefaultValue("10") @Min(0) @Max(100)
        int queue,

        @DefaultValue("300") @Min(60)
        int orphanTtlSeconds,

        @DefaultValue("60") @Min(10)
        int reapIntervalSeconds,

        @DefaultValue("65536") @Min(1024) @Max(1048576)
        int maxSourceBytes,

        @DefaultValue("65536") @Min(0) @Max(1048576)
        int maxCaseInputBytes,

        @DefaultValue("1048576") @Min(1024) @Max(8388608)
        int maxArchiveBytes,

        @DefaultValue("15") @Min(5) @Max(120)
        int startupSlackSeconds,

        @DefaultValue("10") @Min(1) @Max(60)
        int dockerCommandTimeoutSeconds) {

    /** The number of slots to use: the configured value, or half of the cores when it is 0. */
    public int effectiveConcurrency() {
        if (concurrency > 0) {
            return concurrency;
        }
        return Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
    }
}