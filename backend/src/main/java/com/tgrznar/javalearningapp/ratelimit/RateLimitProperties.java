package com.tgrznar.javalearningapp.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Settings of rate limiting and request body limits (prefix "app.rate-limit").
 *
 * @param loginPerIp           login requests per client IP
 * @param registerPerIp        registration requests per client IP
 * @param refreshPerIp         token refresh requests per client IP
 * @param apiPerIp             every other request under /api/v1/ per client IP (a new endpoint is limited by default)
 * @param loginFailuresPerEmail failed logins per normalized e-mail (any IP)
 * @param quizLoadPerUser      quiz loads per student
 * @param maxTrackedKeys       upper bound of tracked keys in each limiter (memory protection)
 * @param trustedProxies       exact addresses of reverse proxies whose X-Forwarded-For is believed; empty = never
 * @param maxBodyBytes         default limit of a request body
 * @param largeBodyBytes       limit of a request body for the paths in {@code largeBodyPrefixes}
 * @param largeBodyPrefixes    path prefixes that may carry large bodies (module theory, later coding tasks)
 */
@Validated
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        @NotNull @Valid Rule loginPerIp,
        @NotNull @Valid Rule registerPerIp,
        @NotNull @Valid Rule refreshPerIp,
        @NotNull @Valid Rule apiPerIp,
        @NotNull @Valid Rule loginFailuresPerEmail,
        @NotNull @Valid Rule quizLoadPerUser,
        @Min(100) int maxTrackedKeys,
        List<String> trustedProxies,
        @Min(1024) long maxBodyBytes,
        @Min(1024) long largeBodyBytes,
        List<String> largeBodyPrefixes) {

    public RateLimitProperties {
        trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
        largeBodyPrefixes = largeBodyPrefixes == null ? List.of() : List.copyOf(largeBodyPrefixes);
    }

    /** At most {@code max} events within {@code window}. */
    public record Rule(@Min(1) int max, @NotNull Duration window) {
    }
}