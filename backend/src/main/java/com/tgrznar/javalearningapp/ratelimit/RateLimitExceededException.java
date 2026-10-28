package com.tgrznar.javalearningapp.ratelimit;

/** A rate limit was reached. The caller may try again after {@link #getRetryAfterSeconds()} seconds. */
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super("Rate limit exceeded");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}