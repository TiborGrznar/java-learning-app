package com.tgrznar.javalearningapp.runner.sandbox;

import java.util.concurrent.Semaphore;

/**
 * Bounds how much work the sandbox accepts: at most {@code permits} runs at once and at most
 * {@code queue} more waiting. Anything beyond that is rejected immediately, so a flood of
 * submissions cannot pile up threads or exhaust the host.
 *
 * <p>Usage: {@code acquire()} before the run and {@code release()} in a finally block.
 */
public final class SandboxLimiter {

    private final Semaphore running;
    private final Semaphore admitted;

    public SandboxLimiter(int permits, int queue) {
        if (permits < 1 || queue < 0) {
            throw new IllegalArgumentException("permits must be >= 1 and queue >= 0");
        }
        this.running = new Semaphore(permits, true);
        this.admitted = new Semaphore(permits + queue);
    }

    /** Waits for a free slot, or throws {@link SandboxBusyException} when even the queue is full. */
    public void acquire() throws InterruptedException {
        if (!admitted.tryAcquire()) {
            throw new SandboxBusyException();
        }
        try {
            running.acquire();
        } catch (InterruptedException | RuntimeException e) {
            admitted.release();
            throw e;
        }
    }

    public void release() {
        running.release();
        admitted.release();
    }
}