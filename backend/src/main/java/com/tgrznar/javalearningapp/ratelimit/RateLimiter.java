package com.tgrznar.javalearningapp.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window limiter: at most {@code maxEvents} events per key within {@code window}.
 * State is kept in memory, so it is lost on restart and not shared between instances.
 *
 * <p>Two ways to use it. {@link #checkAndRecord} counts every call (request limits per IP).
 * {@link #check}, {@link #record} and {@link #clear} count only what the caller records
 * (failed logins: record a failure, clear on success).
 *
 * <p>Keys come from the client (IP addresses, hashed e-mails), so memory is bounded: at most
 * {@code maxKeys} keys are tracked and at most {@code maxEvents} events per key. When the table
 * is full of live keys, a new key is refused ({@link #checkAndRecord} throws) instead of letting
 * the table grow.
 */
public class RateLimiter {

    static final Duration EVICTION_INTERVAL = Duration.ofMinutes(1);

    private final int maxEvents;
    private final Duration window;
    private final int maxKeys;
    private final Clock clock;
    private final Map<String, Deque<Instant>> events = new ConcurrentHashMap<>();
    private volatile Instant lastEviction = Instant.EPOCH;

    public RateLimiter(int maxEvents, Duration window, int maxKeys) {
        this(maxEvents, window, maxKeys, Clock.systemUTC());
    }

    /** Used by tests, which control the time. */
    RateLimiter(int maxEvents, Duration window, int maxKeys, Clock clock) {
        if (maxEvents < 1 || maxKeys < 1 || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("maxEvents and maxKeys must be >= 1 and window positive");
        }
        this.maxEvents = maxEvents;
        this.window = window;
        this.maxKeys = maxKeys;
        this.clock = clock;
    }

    /** Counts this call and throws when the key has already used up the window. */
    public void checkAndRecord(String key) {
        Instant now = clock.instant();
        if (!admit(key, now)) {
            throw new RateLimitExceededException(window.toSeconds());
        }
        long[] retryAfter = {0};
        events.compute(key, (k, existing) -> {
            Deque<Instant> deque = existing == null ? new ArrayDeque<>() : existing;
            prune(deque, now);
            if (deque.size() >= maxEvents) {
                retryAfter[0] = retryAfterSeconds(deque, now);
            } else {
                deque.addLast(now);
            }
            return deque;
        });
        if (retryAfter[0] > 0) {
            throw new RateLimitExceededException(retryAfter[0]);
        }
    }

    /** Throws when the key has already used up the window; does not count itself. */
    public void check(String key) {
        Instant now = clock.instant();
        long[] retryAfter = {0};
        events.computeIfPresent(key, (k, deque) -> {
            prune(deque, now);
            if (deque.size() >= maxEvents) {
                retryAfter[0] = retryAfterSeconds(deque, now);
            }
            return deque.isEmpty() ? null : deque;
        });
        if (retryAfter[0] > 0) {
            throw new RateLimitExceededException(retryAfter[0]);
        }
    }

    /** Records one event. Never throws: when the table is full the event is dropped. */
    public void record(String key) {
        Instant now = clock.instant();
        if (!admit(key, now)) {
            return;
        }
        events.compute(key, (k, existing) -> {
            Deque<Instant> deque = existing == null ? new ArrayDeque<>() : existing;
            prune(deque, now);
            if (deque.size() < maxEvents) {
                deque.addLast(now);
            }
            return deque;
        });
    }

    public void clear(String key) {
        events.remove(key);
    }

    /** Number of tracked keys, for tests. */
    int trackedKeys() {
        return events.size();
    }

    /** True when the key is already tracked or there is room for it (after sweeping expired keys). */
    private boolean admit(String key, Instant now) {
        if (events.size() < maxKeys || events.containsKey(key)) {
            return true;
        }
        evictExpired(now);
        return events.size() < maxKeys || events.containsKey(key);
    }

    private void evictExpired(Instant now) {
        if (Duration.between(lastEviction, now).compareTo(EVICTION_INTERVAL) < 0) {
            return;
        }
        lastEviction = now;
        for (String key : events.keySet()) {
            events.computeIfPresent(key, (k, deque) -> {
                prune(deque, now);
                return deque.isEmpty() ? null : deque;
            });
        }
    }

    private long retryAfterSeconds(Deque<Instant> deque, Instant now) {
        Duration remaining = Duration.between(now, deque.peekFirst().plus(window));
        return Math.max(1, (remaining.toMillis() + 999) / 1000);
    }

    private void prune(Deque<Instant> deque, Instant now) {
        Instant limit = now.minus(window);
        while (!deque.isEmpty() && !deque.peekFirst().isAfter(limit)) {
            deque.pollFirst();
        }
    }
}