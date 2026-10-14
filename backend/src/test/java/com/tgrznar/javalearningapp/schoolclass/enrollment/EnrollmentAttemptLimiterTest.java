package com.tgrznar.javalearningapp.schoolclass.enrollment;

import com.tgrznar.javalearningapp.schoolclass.exception.TooManyAttemptsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnrollmentAttemptLimiterTest {

    private static final Long USER = 1L;

    private final TestClock clock = new TestClock();
    private final EnrollmentAttemptLimiter limiter = new EnrollmentAttemptLimiter(clock);

    @Test
    void userWithoutFailures_isAllowed() {
        assertThatCode(() -> limiter.checkAllowed(USER)).doesNotThrowAnyException();
    }

    @Test
    void fewerFailuresThanTheLimit_areStillAllowed() {
        failTimes(USER, EnrollmentAttemptLimiter.MAX_FAILED_ATTEMPTS - 1);

        assertThatCode(() -> limiter.checkAllowed(USER)).doesNotThrowAnyException();
    }

    @Test
    void reachingTheLimit_blocksUntilTheWindowEnds() {
        failTimes(USER, EnrollmentAttemptLimiter.MAX_FAILED_ATTEMPTS);

        assertThatThrownBy(() -> limiter.checkAllowed(USER))
                .isInstanceOfSatisfying(TooManyAttemptsException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(900));
    }

    @Test
    void retryAfter_shrinksAsTimePasses() {
        failTimes(USER, EnrollmentAttemptLimiter.MAX_FAILED_ATTEMPTS);
        clock.advance(Duration.ofMinutes(5));

        assertThatThrownBy(() -> limiter.checkAllowed(USER))
                .isInstanceOfSatisfying(TooManyAttemptsException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(600));
    }

    @Test
    void block_endsWhenTheWindowHasPassed() {
        failTimes(USER, EnrollmentAttemptLimiter.MAX_FAILED_ATTEMPTS);
        clock.advance(EnrollmentAttemptLimiter.WINDOW);

        assertThatCode(() -> limiter.checkAllowed(USER)).doesNotThrowAnyException();
    }

    @Test
    void failuresOutsideTheWindow_doNotCount() {
        failTimes(USER, 3);
        clock.advance(Duration.ofMinutes(10));
        failTimes(USER, 2);
        // The first three failures are now 16 minutes old and have dropped out, two remain.
        clock.advance(Duration.ofMinutes(6));
        failTimes(USER, 2);
        assertThatCode(() -> limiter.checkAllowed(USER)).doesNotThrowAnyException();

        failTimes(USER, 1);
        assertThatThrownBy(() -> limiter.checkAllowed(USER)).isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void success_clearsTheCounter() {
        failTimes(USER, EnrollmentAttemptLimiter.MAX_FAILED_ATTEMPTS - 1);
        limiter.recordSuccess(USER);
        failTimes(USER, EnrollmentAttemptLimiter.MAX_FAILED_ATTEMPTS - 1);

        assertThatCode(() -> limiter.checkAllowed(USER)).doesNotThrowAnyException();
    }

    @Test
    void limit_isPerUser() {
        failTimes(USER, EnrollmentAttemptLimiter.MAX_FAILED_ATTEMPTS);

        assertThatThrownBy(() -> limiter.checkAllowed(USER)).isInstanceOf(TooManyAttemptsException.class);
        assertThatCode(() -> limiter.checkAllowed(2L)).doesNotThrowAnyException();
    }

    @Test
    void expiredRecords_areEvictedWhenManyUsersAreTracked() {
        for (long userId = 1; userId <= EnrollmentAttemptLimiter.EVICTION_THRESHOLD; userId++) {
            limiter.recordFailure(userId);
        }
        assertThat(limiter.trackedUsers()).isEqualTo(EnrollmentAttemptLimiter.EVICTION_THRESHOLD);

        clock.advance(EnrollmentAttemptLimiter.WINDOW.plusMinutes(1));
        limiter.recordFailure(5_000L);

        assertThat(limiter.trackedUsers()).isEqualTo(1);
    }

    private void failTimes(Long userId, int times) {
        for (int i = 0; i < times; i++) {
            limiter.recordFailure(userId);
        }
    }

    /** A clock that only moves when the test says so. */
    private static final class TestClock extends Clock {

        private Instant now = Instant.parse("2026-10-13T10:00:00Z");

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