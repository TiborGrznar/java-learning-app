package com.tgrznar.javalearningapp.schoolclass.enrollment;

import com.tgrznar.javalearningapp.schoolclass.exception.TooManyAttemptsException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limits failed enrollment attempts per user: at most {@link #MAX_FAILED_ATTEMPTS} failures within
 * {@link #WINDOW}. State is kept in memory, so it is lost on restart and not shared between instances.
 * Only failures are counted; a successful enrollment clears the user's record.
 */
@Component
public class EnrollmentAttemptLimiter {

    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    /** Expired records are swept out only when this many users are tracked, and at most once per interval. */
    static final int EVICTION_THRESHOLD = 1000;
    static final Duration EVICTION_INTERVAL = Duration.ofMinutes(1);

    private final Clock clock;
    private final Map<Long, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private volatile Instant lastEviction = Instant.EPOCH;

    /** Used by Spring. */
    public EnrollmentAttemptLimiter() {
        this(Clock.systemUTC());
    }

    /** Used by tests, which control the time. */
    EnrollmentAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Throws when the user has already used up the failed attempts of the current window. */
    public void checkAllowed(Long userId) {
        Instant now = clock.instant();
        long[] retryAfterSeconds = {0};
        failures.computeIfPresent(userId, (id, attempts) -> {
            prune(attempts, now);
            if (attempts.size() >= MAX_FAILED_ATTEMPTS) {
                Duration remaining = Duration.between(now, attempts.peekFirst().plus(WINDOW));
                retryAfterSeconds[0] = Math.max(1, (remaining.toMillis() + 999) / 1000);
            }
            return attempts.isEmpty() ? null : attempts;
        });
        if (retryAfterSeconds[0] > 0) {
            throw new TooManyAttemptsException(retryAfterSeconds[0]);
        }
    }

    public void recordFailure(Long userId) {
        Instant now = clock.instant();
        evictExpiredIfNeeded(now);
        failures.compute(userId, (id, attempts) -> {
            Deque<Instant> updated = attempts == null ? new ArrayDeque<>() : attempts;
            prune(updated, now);
            updated.addLast(now);
            return updated;
        });
    }

    public void recordSuccess(Long userId) {
        failures.remove(userId);
    }

    /** Number of users with a record, for tests. */
    int trackedUsers() {
        return failures.size();
    }

    private void evictExpiredIfNeeded(Instant now) {
        if (failures.size() < EVICTION_THRESHOLD || Duration.between(lastEviction, now).compareTo(EVICTION_INTERVAL) < 0) {
            return;
        }
        lastEviction = now;
        for (Long userId : failures.keySet()) {
            failures.computeIfPresent(userId, (id, attempts) -> {
                prune(attempts, now);
                return attempts.isEmpty() ? null : attempts;
            });
        }
    }

    /** Drops the failures that are older than the window. */
    private static void prune(Deque<Instant> attempts, Instant now) {
        Instant limit = now.minus(WINDOW);
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(limit)) {
            attempts.pollFirst();
        }
    }
}