package com.tgrznar.javalearningapp.codingtask.admin;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lets only one reference-solution verification run at a time in this application instance. A verification
 * runs up to twenty cases in the sandbox, so an administrator clicking repeatedly (or several of them at once)
 * must not be able to occupy the runner. The state is in memory, like {@code SubmissionGuard}: it is per
 * instance and lost on restart. With several backend instances it would have to become a database lock.
 * <p>
 * The caller must release in a {@code finally} block, otherwise no task can be activated until restart.
 */
@Component
public class VerificationGuard {

    private final AtomicBoolean running = new AtomicBoolean(false);

    /** @return true when no verification was running and now one is; false otherwise. */
    public boolean tryAcquire() {
        return running.compareAndSet(false, true);
    }

    public void release() {
        running.set(false);
    }
}
