package com.tgrznar.javalearningapp.ratelimit;

import java.time.Duration;
import java.util.List;

/** Builds {@link RateLimitProperties} for unit tests; every limit not given is effectively off. */
final class TestRateLimitProperties {

    static final int OFF = 100_000;

    private TestRateLimitProperties() {
    }

    static RateLimitProperties create(int loginMax, int registerMax, int apiMax) {
        return new RateLimitProperties(
                rule(loginMax), rule(registerMax), rule(OFF), rule(apiMax), rule(OFF), rule(OFF),
                1000, List.of(), 100, 1000, List.of("/api/v1/modules"));
    }

    private static RateLimitProperties.Rule rule(int max) {
        return new RateLimitProperties.Rule(max, Duration.ofMinutes(15));
    }
}