package com.tgrznar.javalearningapp.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final TestClock clock = new TestClock();
    private final RateLimiter limiter = new RateLimiter(3, WINDOW, 100, clock);

    @Test
    void unknownKey_isAllowed() {
        assertThatCode(() -> limiter.check("a")).doesNotThrowAnyException();
    }

    @Test
    void checkAndRecord_allowsUpToTheLimitThenThrows() {
        limiter.checkAndRecord("a");
        limiter.checkAndRecord("a");
        limiter.checkAndRecord("a");

        assertThatThrownBy(() -> limiter.checkAndRecord("a"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void retryAfter_isTheTimeUntilTheOldestEventLeavesTheWindow() {
        fill("a", 3);

        assertThatThrownBy(() -> limiter.checkAndRecord("a"))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(900));
    }

    @Test
    void retryAfter_shrinksAsTimePasses() {
        fill("a", 3);
        clock.advance(Duration.ofMinutes(5));

        assertThatThrownBy(() -> limiter.checkAndRecord("a"))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(600));
    }

    @Test
    void block_endsWhenTheWindowHasPassed() {
        fill("a", 3);
        clock.advance(WINDOW);

        assertThatCode(() -> limiter.checkAndRecord("a")).doesNotThrowAnyException();
    }

    @Test
    void check_doesNotCountItself() {
        for (int i = 0; i < 100; i++) {
            limiter.check("a");
        }

        assertThatCode(() -> limiter.check("a")).doesNotThrowAnyException();
        assertThat(limiter.trackedKeys()).isZero();
    }

    @Test
    void record_thenCheck_blocksAtTheLimit() {
        limiter.record("a");
        limiter.record("a");
        assertThatCode(() -> limiter.check("a")).doesNotThrowAnyException();

        limiter.record("a");

        assertThatThrownBy(() -> limiter.check("a")).isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void clear_resetsTheKey() {
        fill("a", 3);
        limiter.clear("a");

        assertThatCode(() -> limiter.checkAndRecord("a")).doesNotThrowAnyException();
    }

    @Test
    void keys_areIndependent() {
        fill("a", 3);

        assertThatThrownBy(() -> limiter.checkAndRecord("a")).isInstanceOf(RateLimitExceededException.class);
        assertThatCode(() -> limiter.checkAndRecord("b")).doesNotThrowAnyException();
    }

    @Test
    void recordingBeyondTheLimit_doesNotKeepTheBlockAlive() {
        for (int i = 0; i < 3; i++) {
            limiter.record("a");
        }
        clock.advance(Duration.ofMinutes(1));
        for (int i = 0; i < 50; i++) {
            limiter.record("a");   // ignored, the record is already full
        }
        clock.advance(Duration.ofMinutes(14));

        assertThatCode(() -> limiter.check("a")).doesNotThrowAnyException();
    }

    @Test
    void fullTable_refusesNewKeysAndRecoversWhenRecordsExpire() {
        RateLimiter small = new RateLimiter(3, WINDOW, 3, clock);
        small.checkAndRecord("a");
        small.checkAndRecord("b");
        small.checkAndRecord("c");

        assertThatThrownBy(() -> small.checkAndRecord("d")).isInstanceOf(RateLimitExceededException.class);
        assertThatCode(() -> small.checkAndRecord("a")).doesNotThrowAnyException();   // known keys keep working

        clock.advance(WINDOW.plusMinutes(1));

        assertThatCode(() -> small.checkAndRecord("d")).doesNotThrowAnyException();
        assertThat(small.trackedKeys()).isEqualTo(1);
    }

    @Test
    void concurrentCalls_neverExceedTheLimit() throws Exception {
        RateLimiter shared = new RateLimiter(10, WINDOW, 100, clock);
        AtomicInteger allowed = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch done = new CountDownLatch(100);
        try {
            for (int i = 0; i < 100; i++) {
                executor.submit(() -> {
                    try {
                        shared.checkAndRecord("a");
                        allowed.incrementAndGet();
                    } catch (RateLimitExceededException e) {
                        // expected for most calls
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(allowed.get()).isEqualTo(10);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void invalidArguments_areRejected() {
        assertThatThrownBy(() -> new RateLimiter(0, WINDOW, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimiter(1, Duration.ZERO, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimiter(1, WINDOW, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private void fill(String key, int times) {
        for (int i = 0; i < times; i++) {
            limiter.checkAndRecord(key);
        }
    }

    /** A clock that only moves when the test says so. */
    private static final class TestClock extends Clock {

        private Instant now = Instant.parse("2026-10-27T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}