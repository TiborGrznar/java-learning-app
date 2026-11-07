package com.tgrznar.javalearningapp.codingtask.admin;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationGuardTest {

    private final VerificationGuard guard = new VerificationGuard();

    @Test
    void tryAcquire_whenFree_succeeds() {
        assertThat(guard.tryAcquire()).isTrue();
    }

    @Test
    void tryAcquire_whenAlreadyRunning_isRefused() {
        guard.tryAcquire();

        assertThat(guard.tryAcquire()).isFalse();
    }

    @Test
    void release_makesTheGuardFreeAgain() {
        guard.tryAcquire();

        guard.release();

        assertThat(guard.tryAcquire()).isTrue();
    }

    @Test
    void release_whenNothingIsRunning_isHarmless() {
        guard.release();

        assertThat(guard.tryAcquire()).isTrue();
        assertThat(guard.tryAcquire()).isFalse();
    }
}
