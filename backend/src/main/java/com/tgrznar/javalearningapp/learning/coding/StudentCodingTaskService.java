package com.tgrznar.javalearningapp.learning.coding;

import com.tgrznar.javalearningapp.coderunner.CodeRunner;
import com.tgrznar.javalearningapp.coderunner.RunnerResponse;
import com.tgrznar.javalearningapp.codingresult.CodingResultRepository;
import com.tgrznar.javalearningapp.codingresult.CodingResultStatus;
import com.tgrznar.javalearningapp.codingtask.CodingTask;
import com.tgrznar.javalearningapp.codingtask.CodingTaskCase;
import com.tgrznar.javalearningapp.codingtask.CodingTaskCaseRepository;
import com.tgrznar.javalearningapp.codingtask.CodingTaskRepository;
import com.tgrznar.javalearningapp.codingtask.exception.CodingTaskNotFoundException;
import com.tgrznar.javalearningapp.learning.ModuleAccessService;
import com.tgrznar.javalearningapp.learning.coding.dto.CodingSubmissionResponse;
import com.tgrznar.javalearningapp.learning.coding.dto.StudentCodingTaskDetail;
import com.tgrznar.javalearningapp.learning.coding.dto.StudentCodingTaskSummary;
import com.tgrznar.javalearningapp.learning.exception.SubmissionInProgressException;
import com.tgrznar.javalearningapp.learning.quiz.exception.TheoryNotCompletedException;
import com.tgrznar.javalearningapp.ratelimit.RateLimiters;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * The student's coding tasks of a module (UC-08, UC-09): the list, the detail and submitting a solution.
 * <p>
 * A submission is evaluated in the sandbox runner, never in this JVM. The class is deliberately not
 * @Transactional: no database transaction may stay open while the runner executes student code. The steps are
 * validate (short read transactions), run, then store the result in one short transaction
 * (CodingSubmissionRecorder). Neither the code nor an expected output is ever logged.
 * <p>
 * Order of the checks of a submission: the rate limit, the module (unlocked, theory read), the task (belongs
 * to the module and is active, otherwise 404), and only then the one-at-a-time guard, so a refused request
 * never blocks the student's real submission.
 */
@Service
@RequiredArgsConstructor
public class StudentCodingTaskService {

    private final ModuleAccessService accessService;
    private final CodingTaskRepository taskRepository;
    private final CodingTaskCaseRepository caseRepository;
    private final CodingResultRepository resultRepository;
    private final CodeRunner codeRunner;
    private final AttemptEvaluator evaluator;
    private final CodingSubmissionRecorder recorder;
    private final SubmissionGuard guard;
    private final RateLimiters rateLimiters;

    @Transactional(readOnly = true)
    public List<StudentCodingTaskSummary> list(Long userId, Long moduleId) {
        requireCodingAccess(userId, moduleId);
        Set<Long> solved = resultRepository.findTaskIdsWithStatus(userId, moduleId, CodingResultStatus.PASSED);
        return taskRepository.findByModuleIdAndActiveTrueOrderByOrderNumberAscIdAsc(moduleId).stream()
                .map(task -> StudentCodingTaskSummary.from(task, solved.contains(task.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public StudentCodingTaskDetail detail(Long userId, Long moduleId, Long taskId) {
        requireCodingAccess(userId, moduleId);
        CodingTask task = activeTask(moduleId, taskId);
        List<CodingTaskCase> cases = caseRepository.findByCodingTaskIdOrderByOrderNumberAsc(task.getId());
        boolean solved = resultRepository.findTaskIdsWithStatus(userId, moduleId, CodingResultStatus.PASSED)
                .contains(task.getId());
        return StudentCodingTaskDetail.from(task, cases, solved);
    }

    public CodingSubmissionResponse submit(Long userId, Long moduleId, Long taskId, String code) {
        rateLimiters.codingSubmitPerUser().checkAndRecord(String.valueOf(userId));
        requireCodingAccess(userId, moduleId);
        CodingTask task = activeTask(moduleId, taskId);
        List<CodingTaskCase> cases = caseRepository.findByCodingTaskIdOrderByOrderNumberAsc(task.getId());
        if (cases.isEmpty()) {
            // An active task always has cases (see the activation rules), so this is a data fault, not the student's.
            throw new IllegalStateException("Active coding task " + taskId + " has no cases");
        }

        if (!guard.tryAcquire(userId)) {
            throw new SubmissionInProgressException();
        }
        try {
            List<String> inputs = cases.stream().map(CodingTaskCase::getInputText).toList();
            List<CaseExpectation> expectations = cases.stream()
                    .map(testCase -> new CaseExpectation(testCase.getOrderNumber(), testCase.getExpectedOutput(), testCase.isHidden()))
                    .toList();

            RunnerResponse response = codeRunner.run(code, task.getDriverCode(), inputs);
            AttemptEvaluation evaluation = evaluator.evaluate(response, expectations, lineCount(code));
            boolean moduleCodingCompleted = recorder.record(userId, moduleId, task.getId(), code, evaluation);
            return CodingSubmissionResponse.from(evaluation, moduleCodingCompleted);
        } finally {
            guard.release(userId);
        }
    }

    /** The module must be unlocked and its theory read. The quiz is not required, coding does not depend on it. */
    private void requireCodingAccess(Long userId, Long moduleId) {
        ModuleAccessService.ModuleAccess access = accessService.requireUnlocked(userId, moduleId);
        if (access.progress() == null || !access.progress().isTheoryCompleted()) {
            throw new TheoryNotCompletedException(moduleId);
        }
    }

    /** A task of another module and an inactive task both look like a task that does not exist. */
    private CodingTask activeTask(Long moduleId, Long taskId) {
        return taskRepository.findByIdAndModuleIdAndActiveTrue(taskId, moduleId)
                .orElseThrow(() -> new CodingTaskNotFoundException(taskId));
    }

    private static int lineCount(String code) {
        return (int) code.lines().count();
    }
}
