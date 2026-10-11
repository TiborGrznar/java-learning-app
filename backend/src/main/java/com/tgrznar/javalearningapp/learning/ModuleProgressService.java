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
        UserProgress progress = access.progress() != null
                ? access.progress()
                : createOrLoad(userId, access.module(), true);

        if (!progress.isTheoryCompleted()) {
            progress.setTheoryCompleted(true);
            progressRepository.save(progress);
        }
    }

    /** The student's progress row in the module. An empty one is created when the student has none yet. */
    public UserProgress ensureProgress(Long userId, ModuleAccessService.ModuleAccess access) {
        return access.progress() != null
                ? access.progress()
                : createOrLoad(userId, access.module(), false);
    }

    private UserProgress createOrLoad(Long userId, CourseModule module, boolean theoryCompleted) {
        UserProgress created = newProgress(userId, module, theoryCompleted);
        try {
            progressRepository.saveAndFlush(created);
            return created;
        } catch (DataIntegrityViolationException e) {
            // A concurrent request created the row between the check and the insert: use that one.
            return progressRepository.findByUserIdAndModuleId(userId, module.getId()).orElseThrow(() -> e);
        }
    }

    private UserProgress newProgress(Long userId, CourseModule module, boolean theoryCompleted) {
        UserProgress progress = new UserProgress();
        progress.setUser(userRepository.getReferenceById(userId));
        progress.setModule(module);
        progress.setTheoryCompleted(theoryCompleted);
        return progress;
    }
}