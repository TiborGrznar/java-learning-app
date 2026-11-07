package com.tgrznar.javalearningapp.learning.coding;

import com.tgrznar.javalearningapp.coderunner.CodeRunner;
import com.tgrznar.javalearningapp.coderunner.CodeRunnerBusyException;
import com.tgrznar.javalearningapp.coderunner.CodeRunnerUnavailableException;
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
import com.tgrznar.javalearningapp.learning.exception.ModuleLockedException;
import com.tgrznar.javalearningapp.learning.quiz.exception.TheoryNotCompletedException;
import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.ratelimit.TestRateLimiters;
import com.tgrznar.javalearningapp.userprogress.UserProgress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentCodingTaskServiceTest {

    private static final Long USER_ID = 5L;
    private static final Long MODULE_ID = 1L;
    private static final Long TASK_ID = 7L;
    private static final String CODE = "class Solution {\n}\n";

    @Mock
    private ModuleAccessService accessService;

    @Mock
    private CodingTaskRepository taskRepository;

    @Mock
    private CodingTaskCaseRepository caseRepository;

    @Mock
    private CodingResultRepository resultRepository;

    @Mock
    private CodeRunner codeRunner;

    @Mock
    private CodingSubmissionRecorder recorder;

    private final SubmissionGuard guard = new SubmissionGuard();

    private StudentCodingTaskService service;

    @BeforeEach
    void setUp() {
        service = new StudentCodingTaskService(accessService, taskRepository, caseRepository, resultRepository,
                codeRunner, new AttemptEvaluator(), recorder, guard, TestRateLimiters.unlimited());
    }

    // ---------- helpers ----------

    private void allowModule(boolean theoryCompleted) {
        UserProgress progress = new UserProgress();
        progress.setTheoryCompleted(theoryCompleted);
        when(accessService.requireUnlocked(USER_ID, MODULE_ID))
                .thenReturn(new ModuleAccessService.ModuleAccess(new CourseModule(), progress, false));
    }

    private CodingTask task() {
        CodingTask task = new CodingTask();
        task.setId(TASK_ID);
        task.setOrderNumber(1);
        task.setTitle("Square");
        task.setDriverCode("DRIVER");
        task.setActive(true);
        return task;
    }

    private static CodingTaskCase testCase(int number, String input, String expected, boolean hidden) {
        CodingTaskCase testCase = new CodingTaskCase();
        testCase.setOrderNumber(number);
        testCase.setInputText(input);
        testCase.setExpectedOutput(expected);
        testCase.setHidden(hidden);
        return testCase;
    }

    private void taskWithTwoCases() {
        when(taskRepository.findByIdAndModuleIdAndActiveTrue(TASK_ID, MODULE_ID)).thenReturn(Optional.of(task()));
        when(caseRepository.findByCodingTaskIdOrderByOrderNumberAsc(TASK_ID)).thenReturn(List.of(
                testCase(1, "2\n", "4\n", false),
                testCase(2, "3\n", "9\n", true)));
    }

    // ---------- list and detail ----------

    @Test
    void list_marksSolvedTasks() {
        allowModule(true);
        CodingTask first = task();
        CodingTask second = task();
        second.setId(8L);
        second.setOrderNumber(2);
        when(taskRepository.findByModuleIdAndActiveTrueOrderByOrderNumberAscIdAsc(MODULE_ID)).thenReturn(List.of(first, second));
        when(resultRepository.findTaskIdsWithStatus(USER_ID, MODULE_ID, CodingResultStatus.PASSED)).thenReturn(Set.of(8L));

        List<StudentCodingTaskSummary> list = service.list(USER_ID, MODULE_ID);

        assertThat(list).extracting(StudentCodingTaskSummary::id).containsExactly(TASK_ID, 8L);
        assertThat(list).extracting(StudentCodingTaskSummary::solved).containsExactly(false, true);
    }

    @Test
    void list_theoryNotCompleted_throws() {
        allowModule(false);

        assertThatThrownBy(() -> service.list(USER_ID, MODULE_ID)).isInstanceOf(TheoryNotCompletedException.class);
    }

    @Test
    void detail_showsOnlyVisibleCasesAndCountsTheHiddenOnes() {
        allowModule(true);
        taskWithTwoCases();
        when(resultRepository.findTaskIdsWithStatus(USER_ID, MODULE_ID, CodingResultStatus.PASSED)).thenReturn(Set.of());

        StudentCodingTaskDetail detail = service.detail(USER_ID, MODULE_ID, TASK_ID);

        assertThat(detail.visibleCases()).hasSize(1);
        assertThat(detail.visibleCases().get(0).expectedOutput()).isEqualTo("4\n");
        assertThat(detail.hiddenCaseCount()).isEqualTo(1);
        assertThat(detail.solved()).isFalse();
    }

    @Test
    void detail_unknownOrInactiveTask_throwsNotFound() {
        allowModule(true);
        when(taskRepository.findByIdAndModuleIdAndActiveTrue(TASK_ID, MODULE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(USER_ID, MODULE_ID, TASK_ID)).isInstanceOf(CodingTaskNotFoundException.class);
    }

    // ---------- submit: refused before the runner ----------

    @Test
    void submit_lockedModule_neverCallsTheRunner() {
        when(accessService.requireUnlocked(USER_ID, MODULE_ID)).thenThrow(new ModuleLockedException(MODULE_ID));

        assertThatThrownBy(() -> service.submit(USER_ID, MODULE_ID, TASK_ID, CODE)).isInstanceOf(ModuleLockedException.class);

        verifyNoInteractions(codeRunner, recorder);
    }

    @Test
    void submit_theoryNotCompleted_neverCallsTheRunner() {
        allowModule(false);

        assertThatThrownBy(() -> service.submit(USER_ID, MODULE_ID, TASK_ID, CODE)).isInstanceOf(TheoryNotCompletedException.class);

        verifyNoInteractions(codeRunner, recorder);
    }

    @Test
    void submit_taskNotInTheModuleOrInactive_neverCallsTheRunner() {
        allowModule(true);
        when(taskRepository.findByIdAndModuleIdAndActiveTrue(TASK_ID, MODULE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(USER_ID, MODULE_ID, TASK_ID, CODE)).isInstanceOf(CodingTaskNotFoundException.class);

        verifyNoInteractions(codeRunner, recorder);
    }

    // ---------- submit: the runner gets only what it needs ----------

    @Test
    void submit_sendsCodeDriverAndInputsInOrder_neverExpectedOutputs() {
        allowModule(true);
        taskWithTwoCases();
        when(codeRunner.run(anyString(), any(), anyList())).thenReturn(
                new RunnerResponse(new RunnerResponse.Compile(true, false, ""), List.of(
                        new RunnerResponse.CaseResult(1, RunnerResponse.CaseStatus.OK, 5, "4\n", ""),
                        new RunnerResponse.CaseResult(2, RunnerResponse.CaseStatus.OK, 5, "9\n", ""))));
        when(recorder.record(eq(USER_ID), eq(MODULE_ID), eq(TASK_ID), eq(CODE), any())).thenReturn(true);

        CodingSubmissionResponse response = service.submit(USER_ID, MODULE_ID, TASK_ID, CODE);

        ArgumentCaptor<List<String>> inputs = ArgumentCaptor.forClass(List.class);
        verify(codeRunner).run(eq(CODE), eq("DRIVER"), inputs.capture());
        assertThat(inputs.getValue()).containsExactly("2\n", "3\n");
        assertThat(response.passed()).isTrue();
        assertThat(response.status()).isEqualTo(CodingResultStatus.PASSED);
        assertThat(response.moduleCodingCompleted()).isTrue();
    }

    @Test
    void submit_storesTheEvaluationOfTheRunnersAnswer() {
        allowModule(true);
        taskWithTwoCases();
        when(codeRunner.run(anyString(), any(), anyList())).thenReturn(
                new RunnerResponse(new RunnerResponse.Compile(true, false, ""), List.of(
                        new RunnerResponse.CaseResult(1, RunnerResponse.CaseStatus.OK, 5, "5\n", ""),
                        new RunnerResponse.CaseResult(2, RunnerResponse.CaseStatus.OK, 5, "9\n", ""))));
        when(recorder.record(eq(USER_ID), eq(MODULE_ID), eq(TASK_ID), eq(CODE), any())).thenReturn(false);

        CodingSubmissionResponse response = service.submit(USER_ID, MODULE_ID, TASK_ID, CODE);

        ArgumentCaptor<AttemptEvaluation> evaluation = ArgumentCaptor.forClass(AttemptEvaluation.class);
        verify(recorder).record(eq(USER_ID), eq(MODULE_ID), eq(TASK_ID), eq(CODE), evaluation.capture());
        assertThat(evaluation.getValue().status()).isEqualTo(CodingResultStatus.FAILED);
        assertThat(evaluation.getValue().passedCases()).isEqualTo(1);
        assertThat(response.passed()).isFalse();
        assertThat(response.moduleCodingCompleted()).isFalse();
    }

    // ---------- submit: the runner fails ----------

    @Test
    void submit_busyRunner_storesNothingAndReleasesTheStudent() {
        allowModule(true);
        taskWithTwoCases();
        when(codeRunner.run(anyString(), any(), anyList())).thenThrow(new CodeRunnerBusyException(5));

        assertThatThrownBy(() -> service.submit(USER_ID, MODULE_ID, TASK_ID, CODE)).isInstanceOf(CodeRunnerBusyException.class);

        verify(recorder, never()).record(any(), any(), any(), any(), any());
        assertThat(guard.tryAcquire(USER_ID)).isTrue();
    }

    @Test
    void submit_unavailableRunner_storesNothingAndReleasesTheStudent() {
        allowModule(true);
        taskWithTwoCases();
        when(codeRunner.run(anyString(), any(), anyList())).thenThrow(new CodeRunnerUnavailableException("down"));

        assertThatThrownBy(() -> service.submit(USER_ID, MODULE_ID, TASK_ID, CODE)).isInstanceOf(CodeRunnerUnavailableException.class);

        verify(recorder, never()).record(any(), any(), any(), any(), any());
        assertThat(guard.tryAcquire(USER_ID)).isTrue();
    }

    @Test
    void submit_taskWithoutCases_isAServerFault() {
        allowModule(true);
        when(taskRepository.findByIdAndModuleIdAndActiveTrue(TASK_ID, MODULE_ID)).thenReturn(Optional.of(task()));
        when(caseRepository.findByCodingTaskIdOrderByOrderNumberAsc(TASK_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.submit(USER_ID, MODULE_ID, TASK_ID, CODE)).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(codeRunner, recorder);
    }

    // ---------- submit: one at a time ----------

    @Test
    void submit_whileAnotherSubmissionOfTheStudentRuns_isRefused() throws Exception {
        allowModule(true);
        taskWithTwoCases();
        CountDownLatch runnerEntered = new CountDownLatch(1);
        CountDownLatch releaseRunner = new CountDownLatch(1);
        when(codeRunner.run(anyString(), any(), anyList())).thenAnswer(invocation -> {
            runnerEntered.countDown();
            releaseRunner.await(10, TimeUnit.SECONDS);
            return new RunnerResponse(new RunnerResponse.Compile(true, false, ""), List.of(
                    new RunnerResponse.CaseResult(1, RunnerResponse.CaseStatus.OK, 5, "4\n", ""),
                    new RunnerResponse.CaseResult(2, RunnerResponse.CaseStatus.OK, 5, "9\n", "")));
        });
        when(recorder.record(any(), any(), any(), any(), any())).thenReturn(true);

        CompletableFuture<CodingSubmissionResponse> first =
                CompletableFuture.supplyAsync(() -> service.submit(USER_ID, MODULE_ID, TASK_ID, CODE));
        assertThat(runnerEntered.await(10, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> service.submit(USER_ID, MODULE_ID, TASK_ID, CODE))
                .isInstanceOf(SubmissionInProgressException.class);

        releaseRunner.countDown();
        assertThat(first.get(10, TimeUnit.SECONDS).passed()).isTrue();
        assertThat(guard.tryAcquire(USER_ID)).isTrue();
    }
}
