package com.tgrznar.javalearningapp.codingtask.admin;

import com.tgrznar.javalearningapp.coderunner.CodeRunnerBusyException;
import com.tgrznar.javalearningapp.codingresult.CodingResultStatus;
import com.tgrznar.javalearningapp.codingtask.dto.CodingTaskResponse;
import com.tgrznar.javalearningapp.codingtask.dto.ReferenceCheckFailure;
import com.tgrznar.javalearningapp.codingtask.exception.CodingTaskChangedException;
import com.tgrznar.javalearningapp.codingtask.exception.CodingTaskNotReadyException;
import com.tgrznar.javalearningapp.codingtask.exception.ReferenceSolutionFailedException;
import com.tgrznar.javalearningapp.codingtask.exception.VerificationInProgressException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The order of the activation steps, and that the guard is held exactly while the sandbox runs. */
@ExtendWith(MockitoExtension.class)
class AdminCodingTaskActivationServiceTest {

    private static final Long ADMIN_ID = 99L;
    private static final Long MODULE_ID = 5L;
    private static final Long TASK_ID = 11L;

    @Mock
    private AdminCodingTaskService taskService;

    @Mock
    private ReferenceSolutionVerifier verifier;

    private VerificationGuard guard;
    private AdminCodingTaskActivationService service;

    @BeforeEach
    void setUp() {
        guard = new VerificationGuard();
        service = new AdminCodingTaskActivationService(taskService, verifier, guard);
    }

    // ---------- deactivation ----------

    @Test
    void setActive_false_deactivatesWithoutVerifying() {
        CodingTaskResponse response = response(false);
        when(taskService.deactivate(MODULE_ID, TASK_ID, ADMIN_ID)).thenReturn(response);

        assertThat(service.setActive(MODULE_ID, TASK_ID, false, ADMIN_ID)).isSameAs(response);

        verifyNoInteractions(verifier);
        verify(taskService, never()).prepareActivation(any(), any());
    }

    // ---------- activation ----------

    @Test
    void setActive_true_preparesVerifiesThenCompletesInThatOrder() {
        ActivationSnapshot snapshot = snapshot();
        CodingTaskResponse response = response(true);
        when(taskService.prepareActivation(MODULE_ID, TASK_ID)).thenReturn(Optional.of(snapshot));
        when(taskService.completeActivation(MODULE_ID, TASK_ID, snapshot, ADMIN_ID)).thenReturn(response);

        assertThat(service.setActive(MODULE_ID, TASK_ID, true, ADMIN_ID)).isSameAs(response);

        InOrder order = inOrder(taskService, verifier);
        order.verify(taskService).prepareActivation(MODULE_ID, TASK_ID);
        order.verify(verifier).verify(snapshot);
        order.verify(taskService).completeActivation(MODULE_ID, TASK_ID, snapshot, ADMIN_ID);
        assertThat(guard.tryAcquire()).as("the guard is free after a successful activation").isTrue();
    }

    @Test
    void setActive_true_alreadyActive_returnsTheTaskWithoutVerifying() {
        CodingTaskResponse response = response(true);
        when(taskService.prepareActivation(MODULE_ID, TASK_ID)).thenReturn(Optional.empty());
        when(taskService.get(MODULE_ID, TASK_ID)).thenReturn(response);

        assertThat(service.setActive(MODULE_ID, TASK_ID, true, ADMIN_ID)).isSameAs(response);

        verifyNoInteractions(verifier);
        verify(taskService, never()).completeActivation(any(), any(), any(), any());
    }

    @Test
    void setActive_true_taskNotReady_failsBeforeTheGuardAndTheRunner() {
        when(taskService.prepareActivation(MODULE_ID, TASK_ID)).thenThrow(new CodingTaskNotReadyException(TASK_ID));

        assertThatThrownBy(() -> service.setActive(MODULE_ID, TASK_ID, true, ADMIN_ID))
                .isInstanceOf(CodingTaskNotReadyException.class);

        verifyNoInteractions(verifier);
        assertThat(guard.tryAcquire()).isTrue();
    }

    @Test
    void setActive_true_failedVerification_activatesNothingAndReleasesTheGuard() {
        ActivationSnapshot snapshot = snapshot();
        when(taskService.prepareActivation(MODULE_ID, TASK_ID)).thenReturn(Optional.of(snapshot));
        doThrow(new ReferenceSolutionFailedException(TASK_ID,
                new ReferenceCheckFailure(CodingResultStatus.FAILED, 1, 0, 1, null, "1\n", "2\n")))
                .when(verifier).verify(snapshot);

        assertThatThrownBy(() -> service.setActive(MODULE_ID, TASK_ID, true, ADMIN_ID))
                .isInstanceOf(ReferenceSolutionFailedException.class);

        verify(taskService, never()).completeActivation(any(), any(), any(), any());
        assertThat(guard.tryAcquire()).isTrue();
    }

    @Test
    void setActive_true_busyRunner_activatesNothingAndReleasesTheGuard() {
        ActivationSnapshot snapshot = snapshot();
        when(taskService.prepareActivation(MODULE_ID, TASK_ID)).thenReturn(Optional.of(snapshot));
        doThrow(new CodeRunnerBusyException(5)).when(verifier).verify(snapshot);

        assertThatThrownBy(() -> service.setActive(MODULE_ID, TASK_ID, true, ADMIN_ID))
                .isInstanceOf(CodeRunnerBusyException.class);

        verify(taskService, never()).completeActivation(any(), any(), any(), any());
        assertThat(guard.tryAcquire()).isTrue();
    }

    @Test
    void setActive_true_taskChangedDuringTheVerification_propagatesAndReleasesTheGuard() {
        ActivationSnapshot snapshot = snapshot();
        when(taskService.prepareActivation(MODULE_ID, TASK_ID)).thenReturn(Optional.of(snapshot));
        when(taskService.completeActivation(MODULE_ID, TASK_ID, snapshot, ADMIN_ID))
                .thenThrow(new CodingTaskChangedException(TASK_ID));

        assertThatThrownBy(() -> service.setActive(MODULE_ID, TASK_ID, true, ADMIN_ID))
                .isInstanceOf(CodingTaskChangedException.class);

        assertThat(guard.tryAcquire()).isTrue();
    }

    // ---------- one verification at a time ----------

    @Test
    void setActive_true_anotherVerificationRunning_isRefusedWithoutRunningAnything() {
        when(taskService.prepareActivation(MODULE_ID, TASK_ID)).thenReturn(Optional.of(snapshot()));
        guard.tryAcquire();

        assertThatThrownBy(() -> service.setActive(MODULE_ID, TASK_ID, true, ADMIN_ID))
                .isInstanceOf(VerificationInProgressException.class);

        verifyNoInteractions(verifier);
        verify(taskService, never()).completeActivation(any(), any(), any(), any());
        assertThat(guard.tryAcquire()).as("the refused request must not release the other verification's guard").isFalse();
    }

    @Test
    void setActive_true_whileOneVerificationIsInTheSandbox_aSecondOneIsRefused() throws Exception {
        ActivationSnapshot snapshot = snapshot();
        when(taskService.prepareActivation(MODULE_ID, TASK_ID)).thenReturn(Optional.of(snapshot));
        when(taskService.completeActivation(MODULE_ID, TASK_ID, snapshot, ADMIN_ID)).thenReturn(response(true));
        CountDownLatch insideSandbox = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        doAnswer(invocation -> {
            insideSandbox.countDown();
            finish.await(5, TimeUnit.SECONDS);
            return null;
        }).when(verifier).verify(snapshot);

        CompletableFuture<CodingTaskResponse> first =
                CompletableFuture.supplyAsync(() -> service.setActive(MODULE_ID, TASK_ID, true, ADMIN_ID));
        assertThat(insideSandbox.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> service.setActive(MODULE_ID, TASK_ID, true, ADMIN_ID))
                .isInstanceOf(VerificationInProgressException.class);

        finish.countDown();
        assertThat(first.get(5, TimeUnit.SECONDS).active()).isTrue();
        assertThat(guard.tryAcquire()).isTrue();
    }

    // ---------- helpers ----------

    private static ActivationSnapshot snapshot() {
        return new ActivationSnapshot(MODULE_ID, TASK_ID, "class Solution {}", null,
                List.of(new ActivationSnapshot.Case(1, "1\n", "1\n")));
    }

    private static CodingTaskResponse response(boolean active) {
        return new CodingTaskResponse(TASK_ID, MODULE_ID, 1, "Task", null, null, null, "class Solution {}", active, 1);
    }
}
