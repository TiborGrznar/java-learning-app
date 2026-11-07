package com.tgrznar.javalearningapp.learning.coding;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lets at most one submission per student be evaluated at a time. Evaluating takes seconds and uses a
 * container of the sandbox, so a student who fires requests in parallel must not be able to occupy
 * several of them. The state is in memory, like the rate limiters: it is per instance and lost on restart.
 * <p>
 * The caller must release in a {@code finally} block, otherwise the student stays blocked until restart.
 */
@Component
public class SubmissionGuard {

    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    /** @return true when the student had no submission in flight and now has one; false otherwise. */
    public boolean tryAcquire(Long userId) {
        return inFlight.add(userId);
    }

    public void release(Long userId) {
        inFlight.remove(userId);
    }

    /** Number of students with a submission in flight, for tests. */
    int inFlightCount() {
        return inFlight.size();
    }
}
