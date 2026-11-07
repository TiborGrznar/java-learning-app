package com.tgrznar.javalearningapp.codingtask.admin;

import com.tgrznar.javalearningapp.codingtask.dto.CodingTaskResponse;
import com.tgrznar.javalearningapp.codingtask.exception.VerificationInProgressException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Activates and deactivates a coding task. Activating a task first proves in the sandbox that its reference
 * solution passes every case, so an unsolvable task (a wrong expected output, a typo in the driver) can never
 * go live.
 * <p>
 * Deliberately not {@code @Transactional}: no database transaction may stay open while the runner executes code.
 * The steps are a short read transaction (take a snapshot), the verification (no transaction), and a short write
 * transaction that activates the task only if it still equals the snapshot. Only one verification runs at a time.
 * Deactivation needs no verification.
 */
@Service
@RequiredArgsConstructor
public class AdminCodingTaskActivationService {

    private final AdminCodingTaskService taskService;
    private final ReferenceSolutionVerifier verifier;
    private final VerificationGuard guard;

    public CodingTaskResponse setActive(Long moduleId, Long id, boolean active, Long adminId) {
        if (!active) {
            return taskService.deactivate(moduleId, id, adminId);
        }

        Optional<ActivationSnapshot> candidate = taskService.prepareActivation(moduleId, id);
        if (candidate.isEmpty()) {
            // Already active: nothing to verify, nothing to change.
            return taskService.get(moduleId, id);
        }
        ActivationSnapshot snapshot = candidate.get();

        // Acquired only after the cheap checks above, so a refused request never blocks a real verification.
        if (!guard.tryAcquire()) {
            throw new VerificationInProgressException();
        }
        try {
            verifier.verify(snapshot);
        } finally {
            guard.release();
        }
        return taskService.completeActivation(moduleId, id, snapshot, adminId);
    }
}
