package com.tgrznar.javalearningapp.runner.sandbox.config;

import com.tgrznar.javalearningapp.runner.sandbox.docker.DockerRunCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the sandbox (prefix "runner.sandbox" in application.yaml). Only values that an
 * operator may reasonably tune live here. The isolation flags of the container (no network,
 * read-only root, dropped capabilities, memory and process limits) are fixed in
 * {@link DockerRunCommand} on purpose, so a configuration mistake cannot weaken them.
 *
 * @param dockerBinary          docker executable (name on PATH or absolute path)
 * @param image                 sandbox image; must be built beforehand, the runner never pulls
 * @param compileTimeoutSeconds time limit for javac inside the container
 * @param runTimeoutSeconds     time limit for one case inside the container
 * @param maxOutputBytes        cap per output stream (compiler output, stdout, stderr of a case)
 * @param maxCases              maximum number of cases per submission (the launcher drops the rest)
 */
@Validated
@ConfigurationProperties(prefix = "runner.sandbox")
public record SandboxProperties(
        @DefaultValue("docker")
        @NotBlank
        String dockerBinary,

        @DefaultValue("jla-sandbox:spike")
        @NotBlank
        @Pattern(regexp = "[a-z0-9][a-z0-9._/:@-]*",
                message = "must be a plain image reference and must not start with '-'")
        String image,

        @DefaultValue("10") @Min(1) @Max(60)
        int compileTimeoutSeconds,

        @DefaultValue("5") @Min(1) @Max(30)
        int runTimeoutSeconds,

        @DefaultValue("65536") @Min(1024) @Max(1048576)
        int maxOutputBytes,

        @DefaultValue("20") @Min(1) @Max(20)
        int maxCases) {
}