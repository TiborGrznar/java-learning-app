package com.tgrznar.javalearningapp.runner.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SandboxLimiterTest {

    @Test
    void invalidArguments_areRejected() {
        assertThatThrownBy(() -> new SandboxLimiter(0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SandboxLimiter(1, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acquire_whenSlotsAreFree_returnsImmediately() throws Exception {
        SandboxLimiter limiter = new SandboxLimiter(2, 0);

        limiter.acquire();
        limiter.acquire();
        limiter.release();
        limiter.release();
    }

    @Test
    void acquire_whenRunningAndQueueAreFull_throwsBusy() throws Exception {
        SandboxLimiter limiter = new SandboxLimiter(1, 0);
        limiter.acquire();

        assertThatThrownBy(limiter::acquire).isInstanceOf(SandboxBusyException.class);
    }

    @Test
    void release_freesTheSlotAgain() throws Exception {
        SandboxLimiter limiter = new SandboxLimiter(1, 0);
        limiter.acquire();
        limiter.release();

        limiter.acquire();
        limiter.release();
    }

    @Test
    void queuedRequest_waitsAndThenRuns_andOneMoreIsRejected() throws Exception {
        SandboxLimiter limiter = new SandboxLimiter(1, 1);
        limiter.acquire();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch waiterStarted = new CountDownLatch(1);
            CountDownLatch waiterGotSlot = new CountDownLatch(1);
            executor.submit(() -> {
                waiterStarted.countDown();
                limiter.acquire();
                waiterGotSlot.countDown();
                return null;
            });
            assertThat(waiterStarted.await(2, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(200);   // let the waiter reach the queue

            assertThat(waiterGotSlot.getCount()).isEqualTo(1);
            assertThatThrownBy(limiter::acquire).isInstanceOf(SandboxBusyException.class);

            limiter.release();
            assertThat(waiterGotSlot.await(2, TimeUnit.SECONDS)).isTrue();
            limiter.release();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void neverRunsMoreThanPermitsAtOnce() throws Exception {
        int permits = 3;
        SandboxLimiter limiter = new SandboxLimiter(permits, 20);
        AtomicInteger current = new AtomicInteger();
        AtomicInteger max = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch done = new CountDownLatch(10);
        try {
            for (int i = 0; i < 10; i++) {
                executor.submit(() -> {
                    try {
                        limiter.acquire();
                        try {
                            int now = current.incrementAndGet();
                            max.accumulateAndGet(now, Math::max);
                            Thread.sleep(30);
                            current.decrementAndGet();
                        } finally {
                            limiter.release();
                        }
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(max.get()).isLessThanOrEqualTo(permits);
        } finally {
            executor.shutdownNow();
        }
    }
}