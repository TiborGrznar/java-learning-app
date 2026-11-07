package com.tgrznar.javalearningapp.learning.coding;

import com.tgrznar.javalearningapp.codingresult.CodingResult;
import com.tgrznar.javalearningapp.codingresult.CodingResultRepository;
import com.tgrznar.javalearningapp.codingresult.CodingResultStatus;
import com.tgrznar.javalearningapp.codingtask.CodingTaskRepository;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.userprogress.UserProgress;
import com.tgrznar.javalearningapp.userprogress.UserProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Stores the result of a submission and, when it completes the coding part of the module, the
 * codingCompleted flag, in one short transaction. It runs after the sandbox finished, so no
 * transaction is held open while student code executes. A separate bean, because a transactional method
 * called from inside its own class would not run in a transaction.
 * <p>
 * The coding part of a module is completed when the student has a PASSED result for every task that is
 * active at that moment. The flag only goes from false to true: later deactivating or adding a task does
 * not take it back.
 * <p>
 * The progress row is locked first. Two submissions of one student then cannot both look at the results
 * before the other is stored and both conclude that something is still missing. The progress row has to
 * exist already, the caller requires the theory to be read, which creates it.
 */
@Service
@RequiredArgsConstructor
public class CodingSubmissionRecorder {

    private final CodingResultRepository resultRepository;
    private final CodingTaskRepository taskRepository;
    private final UserProgressRepository progressRepository;
    private final UserRepository userRepository;

    /**
     * @return whether the coding part of the module is completed after this submission
     */
    @Transactional
    public boolean record(Long userId, Long moduleId, Long taskId, String code, AttemptEvaluation evaluation) {
        UserProgress progress = progressRepository.findForUpdate(userId, moduleId)
                .orElseThrow(() -> new IllegalStateException(
                        "Progress row is missing for user " + userId + " in module " + moduleId));

        CodingResult result = new CodingResult();
        result.setUser(userRepository.getReferenceById(userId));
        result.setCodingTask(taskRepository.getReferenceById(taskId));
        result.setSubmittedCode(code);
        result.setStatus(evaluation.status());
        result.setActualOutput(evaluation.actualOutput());
        result.setErrorMessage(evaluation.errorMessage());
        result.setPassedCases(evaluation.passedCases());
        result.setTotalCases(evaluation.totalCases());
        resultRepository.saveAndFlush(result);

        if (progress.isCodingCompleted()) {
            return true;
        }
        if (!evaluation.passed()) {
            return false;
        }

        Set<Long> passedTaskIds = resultRepository.findTaskIdsWithStatus(userId, moduleId, CodingResultStatus.PASSED);
        List<Long> activeTaskIds = taskRepository.findByModuleIdAndActiveTrueOrderByOrderNumberAscIdAsc(moduleId).stream()
                .map(task -> task.getId())
                .toList();
        if (!passedTaskIds.containsAll(activeTaskIds)) {
            return false;
        }
        progress.setCodingCompleted(true);
        progressRepository.save(progress);
        return true;
    }
}
