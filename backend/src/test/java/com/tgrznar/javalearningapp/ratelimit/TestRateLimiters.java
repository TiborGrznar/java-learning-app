package com.tgrznar.javalearningapp.ratelimit;

import java.time.Duration;
import java.util.List;

/** Limiters with effectively no limit, for unit tests that only need the dependency to exist. */
public final class TestRateLimiters {

    private static final int OFF = 100_000;

    private TestRateLimiters() {
    }

    public static RateLimiters unlimited() {
        RateLimitProperties.Rule rule = new RateLimitProperties.Rule(OFF, Duration.ofMinutes(15));
        return new RateLimiters(new RateLimitProperties(
                rule, rule, rule, rule, rule, rule,
                1000, List.of(), 16384, 1048576, List.of()));
    }
}