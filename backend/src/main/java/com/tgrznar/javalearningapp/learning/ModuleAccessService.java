package com.tgrznar.javalearningapp.learning;

import com.tgrznar.javalearningapp.learning.dto.ModuleStatus;
import com.tgrznar.javalearningapp.learning.exception.ModuleLockedException;
import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.module.CourseModuleRepository;
import com.tgrznar.javalearningapp.module.exception.ModuleNotFoundException;
import com.tgrznar.javalearningapp.userprogress.UserProgress;
import com.tgrznar.javalearningapp.userprogress.UserProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Decides which modules a student may open. A module is unlocked when it is the first active one,
 * when the student passed the quiz of the previous active module, or when the student already has
 * progress in it, so a module opened once never locks again, even if the course order changes later.
 */
@Service
@RequiredArgsConstructor
public class ModuleAccessService {

    private static final Sort COURSE_ORDER = Sort.by("orderNumber", "id");

    private final CourseModuleRepository moduleRepository;
    private final UserProgressRepository progressRepository;

    /** A module in the course with the student's progress (null when there is none) and its lock state. */
    public record ModuleAccess(CourseModule module, UserProgress progress, boolean locked) {
    }

    /** Active modules in course order. Progress is loaded in one query and matched by module id. */
    @Transactional(readOnly = true)
    public List<ModuleAccess> accessFor(Long userId) {
        Map<Long, UserProgress> progressByModule = progressRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(progress -> progress.getModule().getId(), Function.identity()));
        List<CourseModule> modules = moduleRepository.findByActiveTrue(COURSE_ORDER);

        List<ModuleAccess> result = new ArrayList<>(modules.size());
        UserProgress previous = null;
        for (int i = 0; i < modules.size(); i++) {
            CourseModule module = modules.get(i);
            UserProgress progress = progressByModule.get(module.getId());

            boolean unlocked = i == 0
                    || (previous != null && previous.isQuizCompleted())
                    || ModuleStatus.of(progress) != ModuleStatus.NOT_STARTED;

            result.add(new ModuleAccess(module, progress, !unlocked));
            previous = progress;
        }
        return result;
    }

    /** An inactive or unknown module is reported as not found, so its existence is not revealed. */
    @Transactional(readOnly = true)
    public ModuleAccess requireUnlocked(Long userId, Long moduleId) {
        ModuleAccess access = accessFor(userId).stream()
                .filter(candidate -> candidate.module().getId().equals(moduleId))
                .findFirst()
                .orElseThrow(() -> new ModuleNotFoundException(moduleId));

        if (access.locked()) {
            throw new ModuleLockedException(moduleId);
        }
        return access;
    }
}