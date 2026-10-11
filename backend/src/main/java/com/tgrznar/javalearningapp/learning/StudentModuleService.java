package com.tgrznar.javalearningapp.learning;

import com.tgrznar.javalearningapp.learning.dto.ModuleStatus;
import com.tgrznar.javalearningapp.learning.dto.StudentModuleDetail;
import com.tgrznar.javalearningapp.learning.dto.StudentModuleSummary;
import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.module.CourseModuleRepository;
import com.tgrznar.javalearningapp.module.exception.ModuleNotFoundException;
import com.tgrznar.javalearningapp.quizquestion.QuizQuestionRepository;
import com.tgrznar.javalearningapp.userprogress.UserProgress;
import com.tgrznar.javalearningapp.userprogress.UserProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read-only student view of the learning modules (UC-05). Inactive modules are invisible here. */
@Service
@RequiredArgsConstructor
public class StudentModuleService {

    private static final Sort LIST_ORDER = Sort.by("orderNumber", "id");

    private final CourseModuleRepository moduleRepository;
    private final UserProgressRepository progressRepository;
    private final QuizQuestionRepository questionRepository;

    /** Progress is loaded in one query and matched by module id, instead of one query per module. */
    @Transactional(readOnly = true)
    public List<StudentModuleSummary> list(Long userId) {
        Map<Long, UserProgress> progressByModule = progressRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(progress -> progress.getModule().getId(), Function.identity()));

        return moduleRepository.findByActiveTrue(LIST_ORDER).stream()
                .map(module -> StudentModuleSummary.from(module,
                        ModuleStatus.of(progressByModule.get(module.getId()))))
                .toList();
    }

    @Transactional(readOnly = true)
    public StudentModuleDetail get(Long userId, Long moduleId) {
        CourseModule module = moduleRepository.findByIdAndActiveTrue(moduleId)
                .orElseThrow(() -> new ModuleNotFoundException(moduleId));
        UserProgress progress = progressRepository.findByUserIdAndModuleId(userId, moduleId).orElse(null);

        return StudentModuleDetail.from(module, progress, questionRepository.countByModuleId(moduleId));
    }
}