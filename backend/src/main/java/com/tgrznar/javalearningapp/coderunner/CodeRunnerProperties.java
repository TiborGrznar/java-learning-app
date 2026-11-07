package com.tgrznar.javalearningapp.coderunner;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Settings of the connection to the code runner (prefix "app.runner").
 * <p>
 * The read timeout of one call is {@code readTimeoutBase + readTimeoutPerCase * number of cases}. The
 * runner enforces its own limits (compile 10 s, run 5 s per case), this is only the backend's safety
 * net, so the base covers the compile limit plus a margin.
 *
 * @param baseUrl            address of the runner, for example http://127.0.0.1:8081
 * @param token              shared secret sent as X-Runner-Token, at least 32 characters, required
 * @param connectTimeout     how long to wait for the connection
 * @param readTimeoutBase    fixed part of the wait for the answer
 * @param readTimeoutPerCase part of the wait for the answer that grows with every case
 */
@Validated
@ConfigurationProperties(prefix = "app.runner")
public record CodeRunnerProperties(
        @NotBlank String baseUrl,
        @NotBlank @Size(min = 32, message = "the runner token must have at least 32 characters") String token,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeoutBase,
        @NotNull Duration readTimeoutPerCase) {

    /** Keeps the secret out of logs and error reports. */
    @Override
    public String toString() {
        return "CodeRunnerProperties[baseUrl=" + baseUrl + ", token=***]";
    }
}
