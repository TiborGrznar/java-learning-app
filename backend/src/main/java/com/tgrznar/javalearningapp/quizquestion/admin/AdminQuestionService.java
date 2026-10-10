package com.tgrznar.javalearningapp.quizquestion.admin;

import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.module.CourseModuleRepository;
import com.tgrznar.javalearningapp.module.exception.ModuleNotFoundException;
import com.tgrznar.javalearningapp.quizquestion.QuizQuestion;
import com.tgrznar.javalearningapp.quizquestion.QuizQuestionRepository;
import com.tgrznar.javalearningapp.quizquestion.dto.QuestionRequest;
import com.tgrznar.javalearningapp.quizquestion.dto.QuestionResponse;
import com.tgrznar.javalearningapp.quizquestion.exception.QuestionNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Administrator-side management of quiz questions. Every operation is scoped to a module so a
 * question can only be reached through the module it belongs to. Deletion is physical: quiz
 * results store only a score snapshot, so no history points at individual questions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminQuestionService {

    private final QuizQuestionRepository questionRepository;
    private final CourseModuleRepository moduleRepository;

    /** Adds a question to a module; inactive modules are allowed so content can be prepared before publishing. */
    @Transactional
    public QuestionResponse create(Long moduleId, QuestionRequest request, Long adminId) {
        CourseModule module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ModuleNotFoundException(moduleId));

        QuizQuestion question = new QuizQuestion();
        question.setModule(module);
        apply(question, request);
        QuizQuestion saved = questionRepository.saveAndFlush(question);

        log.info("Administrator {} created question {} in module {}", adminId, saved.getId(), moduleId);
        return QuestionResponse.from(saved);
    }

    /** Lists a module's questions in creation order, which is the order students will see them. */
    @Transactional(readOnly = true)
    public List<QuestionResponse> list(Long moduleId) {
        requireModule(moduleId);
        return questionRepository.findByModuleIdOrderByIdAsc(moduleId).stream()
                .map(QuestionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public QuestionResponse get(Long moduleId, Long id) {
        return QuestionResponse.from(findOrThrow(moduleId, id));
    }

    @Transactional
    public QuestionResponse update(Long moduleId, Long id, QuestionRequest request, Long adminId) {
        QuizQuestion question = findOrThrow(moduleId, id);
        apply(question, request);
        questionRepository.flush();

        log.info("Administrator {} updated question {} in module {}", adminId, id, moduleId);
        return QuestionResponse.from(question);
    }

    @Transactional
    public void delete(Long moduleId, Long id, Long adminId) {
        QuizQuestion question = findOrThrow(moduleId, id);
        questionRepository.delete(question);

        log.info("Administrator {} deleted question {} from module {}", adminId, id, moduleId);
    }

    /** Checks the module first so a wrong module id reports MODULE_NOT_FOUND, not QUESTION_NOT_FOUND. */
    private void requireModule(Long moduleId) {
        if (!moduleRepository.existsById(moduleId)) {
            throw new ModuleNotFoundException(moduleId);
        }
    }

    private QuizQuestion findOrThrow(Long moduleId, Long id) {
        requireModule(moduleId);
        return questionRepository.findByIdAndModuleId(id, moduleId)
                .orElseThrow(() -> new QuestionNotFoundException(id));
    }

    /** Copies request fields onto the entity, trimming text so stray whitespace never reaches students. */
    private void apply(QuizQuestion question, QuestionRequest request) {
        question.setQuestionText(request.questionText().trim());
        question.setOptionA(request.optionA().trim());
        question.setOptionB(request.optionB().trim());
        question.setOptionC(request.optionC().trim());
        question.setOptionD(request.optionD().trim());
        question.setCorrectOption(request.correctOption());
    }
}