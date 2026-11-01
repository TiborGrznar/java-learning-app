package com.tgrznar.javalearningapp.learning.coding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubmissionGuardTest {

    private final SubmissionGuard guard = new SubmissionGuard();

    @Test
    void firstSubmissionOfAStudent_isAdmitted() {
        assertThat(guard.tryAcquire(1L)).isTrue();
    }

    @Test
    void secondSubmissionOfTheSameStudent_isRefusedWhileTheFirstRuns() {
        guard.tryAcquire(1L);

        assertThat(guard.tryAcquire(1L)).isFalse();
    }

    @Test
    void otherStudentsAreNotAffected() {
        guard.tryAcquire(1L);

        assertThat(guard.tryAcquire(2L)).isTrue();
    }

    @Test
    void afterRelease_theStudentCanSubmitAgain() {
        guard.tryAcquire(1L);
        guard.release(1L);

        assertThat(guard.tryAcquire(1L)).isTrue();
    }

    @Test
    void releasingWithoutAcquiring_changesNothing() {
        guard.release(1L);

        assertThat(guard.inFlightCount()).isZero();
    }
}
