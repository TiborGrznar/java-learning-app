package com.tgrznar.javalearningapp.codingtask.admin;

import com.tgrznar.javalearningapp.codingtask.CodingTask;
import com.tgrznar.javalearningapp.codingtask.CodingTaskCase;
import com.tgrznar.javalearningapp.codingtask.CodingTaskCaseRepository;
import com.tgrznar.javalearningapp.codingtask.CodingTaskRepository;
import com.tgrznar.javalearningapp.codingtask.dto.CodingTaskCaseRequest;
import com.tgrznar.javalearningapp.codingtask.dto.CodingTaskCaseResponse;
import com.tgrznar.javalearningapp.codingtask.dto.CodingTaskCasesRequest;
import com.tgrznar.javalearningapp.codingtask.dto.CodingTaskRequest;
import com.tgrznar.javalearningapp.codingtask.dto.CodingTaskResponse;
import com.tgrznar.javalearningapp.codingtask.dto.CodingTaskSummary;
import com.tgrznar.javalearningapp.codingtask.exception.CodingTaskHasResultsException;
import com.tgrznar.javalearningapp.codingtask.exception.CodingTaskNotFoundException;
import com.tgrznar.javalearningapp.codingtask.exception.CodingTaskNotReadyException;
import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.module.CourseModuleRepository;
import com.tgrznar.javalearningapp.module.exception.ModuleNotFoundException;
import com.tgrznar.javalearningapp.module.exception.ModuleRequiresActiveTaskException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminCodingTaskServiceTest {

    private static final Long ADMIN_ID = 99L;
    private static final Long MODULE_ID = 5L;
    private static final Long TASK_ID = 11L;

    @Mock
    private CodingTaskRepository taskRepository;

    @Mock
    private CodingTaskCaseRepository caseRepository;

    @Mock
    private CourseModuleRepository moduleRepository;

    @Captor
    private ArgumentCaptor<List<CodingTaskCase>> casesCaptor;

    @InjectMocks
    private AdminCodingTaskService service;

    // ---------- create ----------

    @Test
    void create_savesInactiveTaskWithTrimmedTitleAndBlankFieldsAsNull() {
        givenModule(true);
        when(taskRepository.saveAndFlush(any(CodingTask.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(MODULE_ID, new CodingTaskRequest("  Sucet  ", "   ", "   ", "   ", "   ", 2), ADMIN_ID);

        ArgumentCaptor<CodingTask> captor = ArgumentCaptor.forClass(CodingTask.class);
        verify(taskRepository).saveAndFlush(captor.capture());
        CodingTask saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("Sucet");
        assertThat(saved.getDescription()).isNull();
        assertThat(saved.getTemplateCode()).isNull();
        assertThat(saved.getDriverCode()).isNull();
        assertThat(saved.getReferenceSolution()).isNull();
        assertThat(saved.getOrderNumber()).isEqualTo(2);
        assertThat(saved.isActive()).isFalse();
        assertThat(saved.getModule().getId()).isEqualTo(MODULE_ID);
    }

    @Test
    void create_storesCodeUntrimmedAndDescriptionTrimmed() {
        givenModule(true);
        when(taskRepository.saveAndFlush(any(CodingTask.class))).thenAnswer(inv -> inv.getArgument(0));
        String code = "  class Solution {\n    int x;\n}\n  ";

        service.create(MODULE_ID, new CodingTaskRequest("Task", "  Popis  ", code, code + "1", code + "2", 1), ADMIN_ID);

        ArgumentCaptor<CodingTask> captor = ArgumentCaptor.forClass(CodingTask.class);
        verify(taskRepository).saveAndFlush(captor.capture());
        CodingTask saved = captor.getValue();
        assertThat(saved.getDescription()).isEqualTo("Popis");
        assertThat(saved.getTemplateCode()).isEqualTo(code);
        assertThat(saved.getDriverCode()).isEqualTo(code + "1");
        assertThat(saved.getReferenceSolution()).isEqualTo(code + "2");
    }

    @Test
    void create_returnsTheSavedTaskWithoutCases() {
        givenModule(false);
        when(taskRepository.saveAndFlush(any(CodingTask.class))).thenAnswer(inv -> {
            CodingTask t = inv.getArgument(0);
            t.setId(TASK_ID);
            return t;
        });

        CodingTaskResponse response = service.create(MODULE_ID,
                new CodingTaskRequest("Task", null, null, null, "class Solution {}", 1), ADMIN_ID);

        assertThat(response.id()).isEqualTo(TASK_ID);
        assertThat(response.moduleId()).isEqualTo(MODULE_ID);
        assertThat(response.active()).isFalse();
        assertThat(response.caseCount()).isZero();
        assertThat(response.referenceSolution()).isEqualTo("class Solution {}");
    }

    @Test
    void create_unknownModule_throwsNotFoundAndSavesNothing() {
        when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(MODULE_ID,
                new CodingTaskRequest("Task", null, null, null, null, 1), ADMIN_ID))
                .isInstanceOf(ModuleNotFoundException.class);

        verify(taskRepository, never()).saveAndFlush(any());
    }

    // ---------- list / get ----------

    @Test
    void list_returnsSummariesWithCaseCounts() {
        givenModule(true);
        when(taskRepository.findByModuleIdOrderByOrderNumberAscIdAsc(MODULE_ID))
                .thenReturn(List.of(task(1L, true), task(2L, false)));
        when(caseRepository.countByCodingTaskId(1L)).thenReturn(3L);
        when(caseRepository.countByCodingTaskId(2L)).thenReturn(0L);

        List<CodingTaskSummary> result = service.list(MODULE_ID);

        assertThat(result).extracting(CodingTaskSummary::id).containsExactly(1L, 2L);
        assertThat(result).extracting(CodingTaskSummary::active).containsExactly(true, false);
        assertThat(result).extracting(CodingTaskSummary::caseCount).containsExactly(3L, 0L);
    }

    @Test
    void list_unknownModule_throwsNotFound() {
        when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.list(MODULE_ID)).isInstanceOf(ModuleNotFoundException.class);
        verifyNoInteractions(taskRepository);
    }

    @Test
    void get_returnsFullTaskWithCaseCount() {
        givenModuleAndTask(true, task(TASK_ID, true));
        when(caseRepository.countByCodingTaskId(TASK_ID)).thenReturn(2L);

        CodingTaskResponse response = service.get(MODULE_ID, TASK_ID);

        assertThat(response.id()).isEqualTo(TASK_ID);
        assertThat(response.moduleId()).isEqualTo(MODULE_ID);
        assertThat(response.referenceSolution()).isEqualTo("class Solution {}");
        assertThat(response.caseCount()).isEqualTo(2L);
    }

    @Test
    void get_unknownTask_throwsNotFound() {
        givenModule(true);
        when(taskRepository.findByIdAndModuleId(TASK_ID, MODULE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(MODULE_ID, TASK_ID)).isInstanceOf(CodingTaskNotFoundException.class);
    }

    // ---------- update ----------

    @Test
    void update_replacesAllFieldsAndKeepsTheActiveFlag() {
        CodingTask task = task(TASK_ID, true);
        givenModuleAndTask(true, task);
        when(taskRepository.saveAndFlush(any(CodingTask.class))).thenAnswer(inv -> inv.getArgument(0));

        CodingTaskResponse response = service.update(MODULE_ID, TASK_ID,
                new CodingTaskRequest(" New title ", "New description", "template", "driver", "reference", 7), ADMIN_ID);

        assertThat(response.title()).isEqualTo("New title");
        assertThat(response.description()).isEqualTo("New description");
        assertThat(response.templateCode()).isEqualTo("template");
        assertThat(response.driverCode()).isEqualTo("driver");
        assertThat(response.referenceSolution()).isEqualTo("reference");
        assertThat(response.orderNumber()).isEqualTo(7);
        assertThat(response.active()).isTrue();
    }

    @Test
    void update_unknownTask_throwsNotFoundAndSavesNothing() {
        givenModule(true);
        when(taskRepository.findByIdAndModuleId(TASK_ID, MODULE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(MODULE_ID, TASK_ID,
                new CodingTaskRequest("Task", null, null, null, null, 1), ADMIN_ID))
                .isInstanceOf(CodingTaskNotFoundException.class);

        verify(taskRepository, never()).saveAndFlush(any());
    }

    // ---------- setActive ----------

    @Test
    void setActive_activateWithoutCases_throwsNotReady() {
        givenModuleAndTask(true, task(TASK_ID, false));
        when(caseRepository.countByCodingTaskId(TASK_ID)).thenReturn(0L);

        assertThatThrownBy(() -> service.setActive(MODULE_ID, TASK_ID, true, ADMIN_ID))
                .isInstanceOf(CodingTaskNotReadyException.class);

        verify(taskRepository, never()).saveAndFlush(any());
    }

    @Test
    void setActive_activateWithoutReferenceSolution_throwsNotReady() {
        CodingTask task = task(TASK_ID, false);
        task.setReferenceSolution("   ");
        givenModuleAndTask(true, task);
        when(caseRepository.countByCodingTaskId(TASK_ID)).thenReturn(2L);

        assertThatThrownBy(() -> service.setActive(MODULE_ID, TASK_ID, true, ADMIN_ID))
                .isInstanceOf(CodingTaskNotReadyException.class);

        verify(taskRepository, never()).saveAndFlush(any());
    }

    @Test
    void setActive_activateReadyTask_activates() {
        givenModuleAndTask(true, task(TASK_ID, false));
        when(caseRepository.countByCodingTaskId(TASK_ID)).thenReturn(2L);
        when(taskRepository.saveAndFlush(any(CodingTask.class))).thenAnswer(inv -> inv.getArgument(0));

        CodingTaskResponse response = service.setActive(MODULE_ID, TASK_ID, true, ADMIN_ID);

        assertThat(response.active()).isTrue();
    }

    @Test
    void setActive_sameState_isNoOp() {
        givenModuleAndTask(true, task(TASK_ID, true));

        CodingTaskResponse response = service.setActive(MODULE_ID, TASK_ID, true, ADMIN_ID);

        assertThat(response.active()).isTrue();
        verify(taskRepository, never()).saveAndFlush(any());
    }

    @Test
    void setActive_deactivateLastActiveTaskOfActiveModule_throws() {
        givenModuleAndTask(true, task(TASK_ID, true));
        when(taskRepository.countByModuleIdAndActiveTrue(MODULE_ID)).thenReturn(1L);

        assertThatThrownBy(() -> service.setActive(MODULE_ID, TASK_ID, false, ADMIN_ID))
                .isInstanceOf(ModuleRequiresActiveTaskException.class);

        verify(taskRepository, never()).saveAndFlush(any());
    }

    @Test
    void setActive_deactivateOneOfSeveralActiveTasks_deactivates() {
        givenModuleAndTask(true, task(TASK_ID, true));
        when(taskRepository.countByModuleIdAndActiveTrue(MODULE_ID)).thenReturn(2L);
        when(taskRepository.saveAndFlush(any(CodingTask.class))).thenAnswer(inv -> inv.getArgument(0));

        CodingTaskResponse response = service.setActive(MODULE_ID, TASK_ID, false, ADMIN_ID);

        assertThat(response.active()).isFalse();
    }

    @Test
    void setActive_deactivateLastActiveTaskOfInactiveModule_deactivatesWithoutCounting() {
        givenModuleAndTask(false, task(TASK_ID, true));
        when(taskRepository.saveAndFlush(any(CodingTask.class))).thenAnswer(inv -> inv.getArgument(0));

        CodingTaskResponse response = service.setActive(MODULE_ID, TASK_ID, false, ADMIN_ID);

        assertThat(response.active()).isFalse();
        verify(taskRepository, never()).countByModuleIdAndActiveTrue(any());
    }

    // ---------- delete ----------

    @Test
    void delete_removesCasesBeforeTheTask() {
        CodingTask task = task(TASK_ID, false);
        givenModuleAndTask(true, task);

        service.delete(MODULE_ID, TASK_ID, ADMIN_ID);

        InOrder order = inOrder(caseRepository, taskRepository);
        order.verify(caseRepository).deleteByCodingTaskId(TASK_ID);
        order.verify(taskRepository).delete(task);
        order.verify(taskRepository).flush();
    }

    @Test
    void delete_taskWithResults_throwsAndDeletesNothing() {
        givenModuleAndTask(true, task(TASK_ID, false));
        when(taskRepository.countResults(TASK_ID)).thenReturn(4L);

        assertThatThrownBy(() -> service.delete(MODULE_ID, TASK_ID, ADMIN_ID))
                .isInstanceOf(CodingTaskHasResultsException.class);

        verify(caseRepository, never()).deleteByCodingTaskId(any());
        verify(taskRepository, never()).delete(any(CodingTask.class));
    }

    @Test
    void delete_lastActiveTaskOfActiveModule_throwsAndDeletesNothing() {
        givenModuleAndTask(true, task(TASK_ID, true));
        when(taskRepository.countByModuleIdAndActiveTrue(MODULE_ID)).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(MODULE_ID, TASK_ID, ADMIN_ID))
                .isInstanceOf(ModuleRequiresActiveTaskException.class);

        verify(caseRepository, never()).deleteByCodingTaskId(any());
        verify(taskRepository, never()).delete(any(CodingTask.class));
    }

    @Test
    void delete_inactiveTaskOfActiveModule_doesNotCountActiveTasks() {
        givenModuleAndTask(true, task(TASK_ID, false));

        service.delete(MODULE_ID, TASK_ID, ADMIN_ID);

        verify(taskRepository, never()).countByModuleIdAndActiveTrue(any());
    }

    @Test
    void delete_unknownTask_throwsNotFound() {
        givenModule(true);
        when(taskRepository.findByIdAndModuleId(TASK_ID, MODULE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(MODULE_ID, TASK_ID, ADMIN_ID))
                .isInstanceOf(CodingTaskNotFoundException.class);

        verify(taskRepository, never()).delete(any(CodingTask.class));
    }

    // ---------- cases ----------

    @Test
    void listCases_returnsCasesWithExpectedOutput() {
        givenModuleAndTask(true, task(TASK_ID, true));
        when(caseRepository.findByCodingTaskIdOrderByOrderNumberAsc(TASK_ID))
                .thenReturn(List.of(testCase(1, "1 2", "3", false), testCase(2, "5 5", "10", true)));

        List<CodingTaskCaseResponse> result = service.listCases(MODULE_ID, TASK_ID);

        assertThat(result).extracting(CodingTaskCaseResponse::number).containsExactly(1, 2);
        assertThat(result).extracting(CodingTaskCaseResponse::expectedOutput).containsExactly("3", "10");
        assertThat(result).extracting(CodingTaskCaseResponse::hidden).containsExactly(false, true);
    }

    @Test
    void replaceCases_deletesFlushesThenInsertsNumberedByPosition() {
        CodingTask task = task(TASK_ID, true);
        givenModuleAndTask(true, task);
        when(caseRepository.saveAllAndFlush(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<CodingTaskCaseResponse> result = service.replaceCases(MODULE_ID, TASK_ID,
                new CodingTaskCasesRequest(List.of(
                        new CodingTaskCaseRequest("1 2", "3\n", false),
                        new CodingTaskCaseRequest("", "0\n", true))),
                ADMIN_ID);

        InOrder order = inOrder(caseRepository);
        order.verify(caseRepository).deleteByCodingTaskId(TASK_ID);
        order.verify(caseRepository).flush();
        order.verify(caseRepository).saveAllAndFlush(casesCaptor.capture());

        List<CodingTaskCase> inserted = casesCaptor.getValue();
        assertThat(inserted).extracting(CodingTaskCase::getOrderNumber).containsExactly(1, 2);
        assertThat(inserted).extracting(CodingTaskCase::getInputText).containsExactly("1 2", "");
        assertThat(inserted).extracting(CodingTaskCase::getExpectedOutput).containsExactly("3\n", "0\n");
        assertThat(inserted).extracting(CodingTaskCase::isHidden).containsExactly(false, true);
        assertThat(inserted).allSatisfy(c -> assertThat(c.getCodingTask()).isSameAs(task));
        assertThat(result).extracting(CodingTaskCaseResponse::number).containsExactly(1, 2);
    }

    @Test
    void replaceCases_unknownTask_deletesNothing() {
        givenModule(true);
        when(taskRepository.findByIdAndModuleId(TASK_ID, MODULE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.replaceCases(MODULE_ID, TASK_ID,
                new CodingTaskCasesRequest(List.of(new CodingTaskCaseRequest("1", "1", false))), ADMIN_ID))
                .isInstanceOf(CodingTaskNotFoundException.class);

        verify(caseRepository, never()).deleteByCodingTaskId(any());
        verify(caseRepository, never()).saveAllAndFlush(anyList());
    }

    // ---------- helpers ----------

    private void givenModule(boolean active) {
        when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.of(module(MODULE_ID, active)));
    }

    private void givenModuleAndTask(boolean moduleActive, CodingTask task) {
        givenModule(moduleActive);
        when(taskRepository.findByIdAndModuleId(task.getId(), MODULE_ID)).thenReturn(Optional.of(task));
    }

    private static CourseModule module(Long id, boolean active) {
        CourseModule m = new CourseModule();
        m.setId(id);
        m.setTitle("Module " + id);
        m.setOrderNumber(id.intValue());
        m.setActive(active);
        return m;
    }

    private static CodingTask task(Long id, boolean active) {
        CodingTask t = new CodingTask();
        t.setId(id);
        t.setTitle("Task " + id);
        t.setOrderNumber(1);
        t.setReferenceSolution("class Solution {}");
        t.setActive(active);
        return t;
    }

    private static CodingTaskCase testCase(int number, String input, String expected, boolean hidden) {
        CodingTaskCase c = new CodingTaskCase();
        c.setOrderNumber(number);
        c.setInputText(input);
        c.setExpectedOutput(expected);
        c.setHidden(hidden);
        return c;
    }
}
