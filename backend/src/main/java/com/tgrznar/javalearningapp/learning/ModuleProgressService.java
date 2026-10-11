package com.tgrznar.javalearningapp.learning;

import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.userprogress.UserProgress;
import com.tgrznar.javalearningapp.userprogress.UserProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Records what a student has completed in a module. The completion flags only ever go from false to true.
 * <p>
 * Deliberately not @Transactional: a duplicate-key error caught inside a transaction would mark it
 * rollback-only. Each repository call runs in its own transaction instead.
 */
@Service
@RequiredArgsConstructor
public class ModuleProgressService {

    private final ModuleAccessService accessService;
    private final UserProgressRepository progressRepository;
    private final UserRepository userRepository;

    /** Idempotent: marking a theory that is already marked changes nothing. */
    public void markTheoryCompleted(Long userId, Long moduleId) {
        ModuleAccessService.ModuleAccess access = accessService.requireUnlocked(userId, moduleId);
        UserProgress progress = access.progress();

        if (progress == null) {
            try {
                progressRepository.saveAndFlush(newProgress(userId, access.module()));
                return;
            } catch (DataIntegrityViolationException e) {
                // A concurrent request created the row between the check and the insert: update that one.
                progress = progressRepository.findByUserIdAndModuleId(userId, moduleId).orElseThrow(() -> e);
            }
        }

        if (!progress.isTheoryCompleted()) {
            progress.setTheoryCompleted(true);
            progressRepository.save(progress);
        }
    }

    private UserProgress newProgress(Long userId, CourseModule module) {
        UserProgress progress = new UserProgress();
        progress.setUser(userRepository.getReferenceById(userId));
        progress.setModule(module);
        progress.setTheoryCompleted(true);
        return progress;
    }
}