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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Administrator-side management of coding tasks and their test cases. Every operation is scoped
 * to a module, so a task can only be reached through the module it belongs to.
 * <p>
 * Rules enforced here:
 * <ul>
 *   <li>a new task is inactive; activating it needs a reference solution and at least one case,</li>
 *   <li>an active module keeps at least one active task, so the last active task of an active
 *       module can be neither deactivated nor deleted,</li>
 *   <li>a task with submitted results is never deleted (the results stay as history), only deactivated.</li>
 * </ul>
 * The driver and the reference solution are admin-written code. They are stored here and run only
 * inside the sandbox (never in this JVM), and they never reach a student DTO.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCodingTaskService {

    private final CodingTaskRepository taskRepository;
    private final CodingTaskCaseRepository caseRepository;
    private final CourseModuleRepository moduleRepository;

    /** Adds an inactive task; inactive modules are allowed so content can be prepared before publishing. */
    @Transactional
    public CodingTaskResponse create(Long moduleId, CodingTaskRequest request, Long adminId) {
        CourseModule module = requireModule(moduleId);

        CodingTask task = new CodingTask();
        task.setModule(module);
        task.setActive(false);
        apply(task, request);
        CodingTask saved = taskRepository.saveAndFlush(task);

        log.info("Coding task created: id={}, moduleId={}, createdByAdminId={}", saved.getId(), moduleId, adminId);
        return CodingTaskResponse.from(saved, moduleId, 0);
    }

    /** A module's tasks, active and inactive, in the order students will see them. */
    @Transactional(readOnly = true)
    public List<CodingTaskSummary> list(Long moduleId) {
        requireModule(moduleId);
        return taskRepository.findByModuleIdOrderByOrderNumberAscIdAsc(moduleId).stream()
                .map(task -> CodingTaskSummary.from(task, caseRepository.countByCodingTaskId(task.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CodingTaskResponse get(Long moduleId, Long id) {
        requireModule(moduleId);
        CodingTask task = findTask(moduleId, id);
        return CodingTaskResponse.from(task, moduleId, caseRepository.countByCodingTaskId(id));
    }

    /** Replaces all content fields; the active flag is left alone. */
    @Transactional
    public CodingTaskResponse update(Long moduleId, Long id, CodingTaskRequest request, Long adminId) {
        requireModule(moduleId);
        CodingTask task = findTask(moduleId, id);
        apply(task, request);

        CodingTask saved = taskRepository.saveAndFlush(task);
        log.info("Coding task updated: id={}, moduleId={}, updatedByAdminId={}", id, moduleId, adminId);
        return CodingTaskResponse.from(saved, moduleId, caseRepository.countByCodingTaskId(id));
    }

    @Transactional
    public CodingTaskResponse setActive(Long moduleId, Long id, boolean active, Long adminId) {
        CourseModule module = requireModule(moduleId);
        CodingTask task = findTask(moduleId, id);
        long caseCount = caseRepository.countByCodingTaskId(id);
        if (task.isActive() == active) {
            return CodingTaskResponse.from(task, moduleId, caseCount);
        }

        if (active) {
            requireReady(task, caseCount);
        } else {
            requireNotLastActiveTask(module, task);
        }

        task.setActive(active);
        CodingTask saved = taskRepository.saveAndFlush(task);
        log.info("Coding task active flag changed: id={}, moduleId={}, active={}, changedByAdminId={}",
                id, moduleId, active, adminId);
        return CodingTaskResponse.from(saved, moduleId, caseCount);
    }

    /**
     * Deletes a task together with its cases. Refused when students have submitted results (they
     * are history and the foreign key would block it anyway) and when it is the last active task of
     * an active module.
     */
    @Transactional
    public void delete(Long moduleId, Long id, Long adminId) {
        CourseModule module = requireModule(moduleId);
        CodingTask task = findTask(moduleId, id);

        if (taskRepository.countResults(id) > 0) {
            throw new CodingTaskHasResultsException(id);
        }
        requireNotLastActiveTask(module, task);

        // Cases first, in the same flush order: the database would cascade, but this keeps the
        // persistence context free of cases whose task is gone.
        caseRepository.deleteByCodingTaskId(id);
        taskRepository.delete(task);
        taskRepository.flush();

        log.info("Coding task deleted: id={}, moduleId={}, deletedByAdminId={}", id, moduleId, adminId);
    }

    @Transactional(readOnly = true)
    public List<CodingTaskCaseResponse> listCases(Long moduleId, Long id) {
        requireModule(moduleId);
        findTask(moduleId, id);
        return caseRepository.findByCodingTaskIdOrderByOrderNumberAsc(id).stream()
                .map(CodingTaskCaseResponse::from)
                .toList();
    }

    /**
     * Replaces the whole list of cases. The numbers are the positions in the request, starting at 1.
     * The old cases are deleted and flushed before the new ones are inserted, otherwise Hibernate
     * (which runs inserts before deletes) would hit the unique (task, case number) key.
     * Results of earlier submissions are snapshots, so editing cases never rewrites history.
     */
    @Transactional
    public List<CodingTaskCaseResponse> replaceCases(Long moduleId, Long id, CodingTaskCasesRequest request, Long adminId) {
        requireModule(moduleId);
        CodingTask task = findTask(moduleId, id);

        caseRepository.deleteByCodingTaskId(id);
        caseRepository.flush();

        List<CodingTaskCase> cases = new ArrayList<>();
        int number = 1;
        for (CodingTaskCaseRequest item : request.cases()) {
            CodingTaskCase testCase = new CodingTaskCase();
            testCase.setCodingTask(task);
            testCase.setOrderNumber(number++);
            testCase.setInputText(item.inputText());
            testCase.setExpectedOutput(item.expectedOutput());
            testCase.setHidden(item.hidden());
            cases.add(testCase);
        }
        List<CodingTaskCase> saved = caseRepository.saveAllAndFlush(cases);

        log.info("Coding task cases replaced: taskId={}, moduleId={}, count={}, changedByAdminId={}",
                id, moduleId, saved.size(), adminId);
        return saved.stream().map(CodingTaskCaseResponse::from).toList();
    }

    /** Checks the module first so a wrong module id reports MODULE_NOT_FOUND, not CODING_TASK_NOT_FOUND. */
    private CourseModule requireModule(Long moduleId) {
        return moduleRepository.findById(moduleId).orElseThrow(() -> new ModuleNotFoundException(moduleId));
    }

    /** Scoping by module makes a task reached through another module's URL look like "not found". */
    private CodingTask findTask(Long moduleId, Long id) {
        return taskRepository.findByIdAndModuleId(id, moduleId)
                .orElseThrow(() -> new CodingTaskNotFoundException(id));
    }

    /** Activation needs something to check against (cases) and something to prove it can be solved. */
    private static void requireReady(CodingTask task, long caseCount) {
        if (caseCount == 0 || task.getReferenceSolution() == null || task.getReferenceSolution().isBlank()) {
            throw new CodingTaskNotReadyException(task.getId());
        }
    }

    /** An active module must keep an active task; for an inactive module nothing is lost. */
    private void requireNotLastActiveTask(CourseModule module, CodingTask task) {
        if (task.isActive() && module.isActive()
                && taskRepository.countByModuleIdAndActiveTrue(module.getId()) <= 1) {
            throw new ModuleRequiresActiveTaskException(module.getId());
        }
    }

    /** Copies the request onto the entity. Never touches id, module or active. */
    private static void apply(CodingTask task, CodingTaskRequest request) {
        task.setTitle(request.title().trim());
        task.setDescription(trimToNull(request.description()));
        // Code is stored untrimmed: indentation and trailing newlines are part of it.
        task.setTemplateCode(blankToNull(request.templateCode()));
        task.setDriverCode(blankToNull(request.driverCode()));
        task.setReferenceSolution(blankToNull(request.referenceSolution()));
        task.setOrderNumber(request.orderNumber());
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
