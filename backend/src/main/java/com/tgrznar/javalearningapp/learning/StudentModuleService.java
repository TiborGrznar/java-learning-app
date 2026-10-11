package com.tgrznar.javalearningapp.learning;

import com.tgrznar.javalearningapp.learning.dto.StudentModuleDetail;
import com.tgrznar.javalearningapp.learning.dto.StudentModuleSummary;
import com.tgrznar.javalearningapp.learning.quiz.QuizProperties;
import com.tgrznar.javalearningapp.quizquestion.QuizQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Read-only student view of the learning modules (UC-05). Inactive modules are invisible here. */
@Service
@RequiredArgsConstructor
public class StudentModuleService {

    private final ModuleAccessService accessService;
    private final QuizQuestionRepository questionRepository;
    private final QuizProperties quizProperties;

    @Transactional(readOnly = true)
    public List<StudentModuleSummary> list(Long userId) {
        return accessService.accessFor(userId).stream()
                .map(access -> StudentModuleSummary.from(access.module(), access.progress(), access.locked()))
                .toList();
    }

    /** The question count is what one attempt contains, not the size of the question bank. */
    @Transactional(readOnly = true)
    public StudentModuleDetail get(Long userId, Long moduleId) {
        ModuleAccessService.ModuleAccess access = accessService.requireUnlocked(userId, moduleId);
        long questionsInBank = questionRepository.countByModuleId(moduleId);
        return StudentModuleDetail.from(access.module(), access.progress(),
                Math.min(questionsInBank, quizProperties.questionsPerAttempt()));
    }
}