package com.tgrznar.javalearningapp.codingtask.admin.api;

import com.tgrznar.javalearningapp.auth.security.JwtService;
import com.tgrznar.javalearningapp.codingresult.CodingResult;
import com.tgrznar.javalearningapp.codingresult.CodingResultStatus;
import com.tgrznar.javalearningapp.codingtask.CodingTask;
import com.tgrznar.javalearningapp.codingtask.CodingTaskCase;
import com.tgrznar.javalearningapp.codingtask.CodingTaskCaseRepository;
import com.tgrznar.javalearningapp.codingtask.CodingTaskRepository;
import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.module.CourseModuleRepository;
import com.tgrznar.javalearningapp.testsupport.TestUsers;
import com.tgrznar.javalearningapp.testsupport.TestcontainersConfiguration;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests of the administrator's coding task and test case management
 * (/api/v1/modules/{moduleId}/coding-tasks). Test data is created with unique e-mails and rolled
 * back after each test; every test works in its own module, so lists contain only its own tasks.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
class AdminCodingTaskApiIntegrationTest {

    private static final long UNKNOWN_ID = 999_999_999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestUsers testUsers;

    @Autowired
    private CourseModuleRepository moduleRepository;

    @Autowired
    private CodingTaskRepository taskRepository;

    @Autowired
    private CodingTaskCaseRepository caseRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private EntityManager entityManager;

    // ---------- access control: wrong role = 403, no token = 401 ----------

    @Test
    void list_withoutToken_returns401() throws Exception {
        CourseModule module = saveModule(true);

        mockMvc.perform(get(tasksUrl(module.getId())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void create_withoutToken_returns401() throws Exception {
        CourseModule module = saveModule(true);

        mockMvc.perform(post(tasksUrl(module.getId())).contentType(MediaType.APPLICATION_JSON).content(validTaskJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"STUDENT", "TEACHER"})
    void list_withoutAdminRole_returns403(UserRole role) throws Exception {
        CourseModule module = saveModule(true);

        as(get(tasksUrl(module.getId())), role)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"STUDENT", "TEACHER"})
    void get_withoutAdminRole_returns403(UserRole role) throws Exception {
        CourseModule module = saveModule(true);
        CodingTask task = saveTask(module, "Task", 1, true);

        as(get(taskUrl(module.getId(), task.getId())), role)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"STUDENT", "TEACHER"})
    void create_withoutAdminRole_returns403AndCreatesNothing(UserRole role) throws Exception {
        CourseModule module = saveModule(true);

        as(post(tasksUrl(module.getId())).contentType(MediaType.APPLICATION_JSON).content(validTaskJson()), role)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(taskRepository.findByModuleId(module.getId())).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"STUDENT", "TEACHER"})
    void update_withoutAdminRole_returns403AndChangesNothing(UserRole role) throws Exception {
        CourseModule module = saveModule(true);
        CodingTask task = saveTask(module, "Original", 1, false);

        as(put(taskUrl(module.getId(), task.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(taskJson("Hacked", null, null, null, "class Hacked {}", 1)), role)
                .andExpect(status().isForbidden());

        assertThat(taskRepository.findById(task.getId()).orElseThrow().getTitle()).isEqualTo("Original");
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"STUDENT", "TEACHER"})
    void setActive_withoutAdminRole_returns403AndChangesNothing(UserRole role) throws Exception {
        CourseModule module = saveModule(true);
        CodingTask task = saveTask(module, "Task", 1, false);
        saveCase(task, 1, "1", "1", false);

        as(patch(taskUrl(module.getId(), task.getId()) + "/active").contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":true}"), role)
                .andExpect(status().isForbidden());

        assertThat(taskRepository.findById(task.getId()).orElseThrow().isActive()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"STUDENT", "TEACHER"})
    void delete_withoutAdminRole_returns403AndDeletesNothing(UserRole role) throws Exception {
        CourseModule module = saveModule(true);
        CodingTask task = saveTask(module, "Task", 1, false);

        as(delete(taskUrl(module.getId(), task.getId())), role)
                .andExpect(status().isForbidden());

        assertThat(taskRepository.findById(task.getId())).isPresent();
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"STUDENT", "TEACHER"})
    void listCases_withoutAdminRole_returns403(UserRole role) throws Exception {
        CourseModule module = saveModule(true);
        CodingTask task = saveTask(module, "Task", 1, false);
        saveCase(task, 1, "1", "secret answer", true);

        as(get(casesUrl(module.getId(), task.getId())), role)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"STUDENT", "TEACHER"})
    void replaceCases_withoutAdminRole_returns403AndChangesNothing(UserRole role) throws Exception {
        CourseModule module = saveModule(true);
        CodingTask task = saveTask(module, "Task", 1, false);
        saveCase(task, 1, "1", "1", false);

        as(put(casesUrl(module.getId(), task.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(casesJson(caseJson("2", "2", false), caseJson("3", "3", false))), role)
                .andExpect(status().isForbidden());

        assertThat(caseRepository.countByCodingTaskId(task.getId())).isEqualTo(1);
    }

    // ---------- create ----------

    @Test
    void create_returns201WithLocationAndFullBodyAndStartsInactive() throws Exception {
        CourseModule module = saveModule(false);

        as(post(tasksUrl(module.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(taskJson("Sucet", "Spocitaj dve cisla", "class Solution {}", "class Main {}", "class Solution { }", 2)),
                UserRole.ADMIN)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/modules/" + module.getId() + "/coding-tasks/")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.moduleId").value(module.getId()))
                .andExpect(jsonPath("$.title").value("Sucet"))
                .andExpect(jsonPath("$.description").value("Spocitaj dve cisla"))
                .andExpect(jsonPath("$.templateCode").value("class Solution {}"))
                .andExpect(jsonPath("$.driverCode").value("class Main {}"))
                .andExpect(jsonPath("$.referenceSolution").value("class Solution { }"))
                .andExpect(jsonPath("$.orderNumber").value(2))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.caseCount").value(0));
    }

    @Test
    void create_trimsTitleAndStoresBlankFieldsAsNull() throws Exception {
        CourseModule module = saveModule(true);

        as(post(tasksUrl(module.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(taskJson("  Cykly  ", "   ", "   ", "   ", "   ", 1)), UserRole.ADMIN)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Cykly"))
                .andExpect(jsonPath("$.description").isEmpty())
                .andExpect(jsonPath("$.templateCode").isEmpty())
                .andExpect(jsonPath("$.driverCode").isEmpty())
                .andExpect(jsonPath("$.referenceSolution").isEmpty());
    }

    @Test
    void create_keepsCodeUnchanged() throws Exception {
        CourseModule module = saveModule(true);
        String code = "  class Solution {\n    int x;\n}\n";

        as(post(tasksUrl(module.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(taskJson("Task", null, code, code, code, 1)), UserRole.ADMIN)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.templateCode").value(code))
                .andExpect(jsonPath("$.driverCode").value(code))
                .andExpect(jsonPath("$.referenceSolution").value(code));
    }

    @Test
    void create_unknownModule_returns404() throws Exception {
        as(post(tasksUrl(UNKNOWN_ID)).contentType(MediaType.APPLICATION_JSON).content(validTaskJson()), UserRole.ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_FOUND"));
    }

    @Test
    void create_blankTitle_returns400WithFieldError() throws Exception {
        CourseModule module = saveModule(true);

        as(post(tasksUrl(module.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(taskJson("   ", null, null, null, null, 1)), UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    @Test
    void create_missingOrderNumber_returns400WithFieldError() throws Exception {
        CourseModule module = saveModule(true);

        as(post(tasksUrl(module.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(taskJson("Task", null, null, null, null, null)), UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.orderNumber").exists());
    }

    @Test
    void create_descriptionTooLong_returns400WithFieldError() throws Exception {
        CourseModule module = saveModule(true);

        as(post(tasksUrl(module.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(taskJson("Task", "a".repeat(2001), null, null, null, 1)), UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.description").exists());
    }

    @Test
    void create_codeFieldsTooLong_return400WithFieldErrors() throws Exception {
        CourseModule module = saveModule(true);
        String tooLong = "a".repeat(16_385);

        as(post(tasksUrl(module.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(taskJson("Task", null, tooLong, tooLong, tooLong, 1)), UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.templateCode").exists())
                .andExpect(jsonPath("$.fieldErrors.driverCode").exists())
                .andExpect(jsonPath("$.fieldErrors.referenceSolution").exists());
    }

    @Test
    void create_maximumSizedCodeFields_areAcceptedUnderTheLargeBodyLimit() throws Exception {
        CourseModule module = saveModule(true);
        String maximum = "a".repeat(16_384);

        as(post(tasksUrl(module.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(taskJson("Task", null, maximum, maximum, maximum, 1)), UserRole.ADMIN)
                .andExpect(status().isCreated());
    }

    // ---------- read ----------

    @Test
    void list_includesInactiveTasksOrderedByOrderNumberThenIdWithoutCode() throws Exception {
        CourseModule module = saveModule(true);
        CodingTask later = saveTask(module, "Later", 2, true);
        CodingTask firstOfTwo = saveTask(module, "First of two", 1, false);
        CodingTask secondOfTwo = saveTask(module, "Second of two", 1, true);
        saveCase(later, 1, "1", "1", false);
        saveCase(later, 2, "2", "2", true);

        as(get(tasksUrl(module.getId())), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(firstOfTwo.getId()))
                .andExpect(jsonPath("$[0].active").value(false))
                .andExpect(jsonPath("$[1].id").value(secondOfTwo.getId()))
                .andExpect(jsonPath("$[2].id").value(later.getId()))
                .andExpect(jsonPath("$[2].caseCount").value(2))
                .andExpect(jsonPath("$[0].referenceSolution").doesNotExist())
                .andExpect(jsonPath("$[0].driverCode").doesNotExist());
    }

    @Test
    void list_doesNotIncludeTasksOfOtherModules() throws Exception {
        CourseModule module = saveModule(true);
        CourseModule other = saveModule(true);
        saveTask(module, "Mine", 1, true);
        saveTask(other, "Not mine", 1, true);

        as(get(tasksUrl(module.getId())), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Mine"));
    }

    @Test
    void list_unknownModule_returns404() throws Exception {
        as(get(tasksUrl(UNKNOWN_ID)), UserRole.ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_FOUND"));
    }

    @Test
    void get_returnsFullTaskIncludingDriverAndReferenceSolution() throws Exception {
        CourseModule module = saveModule(true);
        CodingTask task = saveTask(module, "Detail", 3, true);
        task.setDriverCode("class Main {}");
        taskRepository.saveAndFlush(task);
        saveCase(task, 1, "1", "1", false);

        as(get(taskUrl(module.getId(), task.getId())), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(task.getId()))
                .andExpect(jsonPath("$.moduleId").value(module.getId()))
                .andExpect(jsonPath("$.title").value("Detail"))
                .andExpect(jsonPath("$.orderNumber").value(3))
                .andExpect(jsonPath("$.driverCode").value("class Main {}"))
                .andExpect(jsonPath("$.referenceSolution").value("class Solution { }"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.caseCount").value(1));
    }

    @Test
    void get_unknownTask_returns404() throws Exception {
        CourseModule module = saveModule(true);

        as(get(taskUrl(module.getId(), UNKNOWN_ID)), UserRole.ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CODING_TASK_NOT_FOUND"));
    }

    @Test
    void get_taskThroughAnotherModule_returns404() throws Exception {
        CourseModule module = saveModule(true);
        CourseModule other = saveModule(true);
        CodingTask task = saveTask(module, "Task", 1, true);

        as(get(taskUrl(other.getId(), task.getId())), UserRole.ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CODING_TASK_NOT_FOUND"));
    }

    @Test
    void get_unknownModule_returns404() throws Exception {
        as(get(taskUrl(UNKNOWN_ID, 1L)), UserRole.ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_FOUND"));
    }

    // ---------- update ----------

    @Test
    void update_replacesAllFieldsAndKeepsTheActiveFlag() throws Exception {
        CourseModule module = saveModule(true);
        CodingTask task = saveTask(module, "Original", 1, true);

        as(put(taskUrl(module.getId(), task.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(taskJson(" New title ", "New description", "template", "driver", "reference", 7)), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New title"))
                .andExpect(jsonPath("$.description").value("New description"))
                .andExpect(jsonPath("$.templateCode").value("template"))
                .andExpect(jsonPath("$.driverCode").value("driver"))
                .andExpect(jsonPath("$.referenceSolution").value("reference"))
                .andExpect(jsonPath("$.orderNumber").value(7))
                .andExpect(jsonPath("$.active").value(true));

        assertThat(taskRepository.findById(task.getId()).orElseThrow().getTitle()).isEqualTo("New title");
    }

    @Test
    void update_unknownTask_returns404() throws Exception {
        CourseModule module = saveModule(true);

        as(put(taskUrl(module.getId(), UNKNOWN_ID)).contentType(MediaType.APPLICATION_JSON).content(validTaskJson()),
                UserRole.ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CODING_TASK_NOT_FOUND"));
    }

    @Test
    void update_blankTitle_returns400WithFieldError() throws Exception {
        CourseModule module = saveModule(true);
        CodingTask task = saveTask(module, "Original", 1, false);

        as(put(taskUrl(module.getId(), task.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(taskJson("", null, null, null, null, 1)), UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    // ---------- activate / deactivate ----------

    @Test
    void setActive_withoutCases_returns409AndStaysInactive() throws Exception {
        CourseModule module = saveModule(false);
        CodingTask task = saveTask(module, "Task", 1, false);

        as(patchActive(module, task, true), UserRole.ADMIN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CODING_TASK_NOT_READY"));

        assertThat(taskRepository.findById(task.getId()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void setActive_withoutReferenceSolution_returns409AndStaysInactive() throws Exception {
        CourseModule module = saveModule(false);
        CodingTask task = saveTask(module, "Task", 1, false);
        task.setReferenceSolution(null);
        taskRepository.saveAndFlush(task);
        saveCase(task, 1, "1", "1", false);

        as(patchActive(module, task, true), UserRole.ADMIN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CODING_TASK_NOT_READY"));

        assertThat(taskRepository.findById(task.getId()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void setActive_readyTask_activates() throws Exception {
        CourseModule module = saveModule(false);
        CodingTask task = saveTask(module, "Task", 1, false);
        saveCase(task, 1, "1", "1", false);

        as(patchActive(module, task, true), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.caseCount").value(1));

        assertThat(taskRepository.findById(task.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void setActive_deactivateLastActiveTaskOfActiveModule_returns409AndStaysActive() throws Exception {
        CourseModule module = saveModule(true);
        CodingTask task = saveTask(module, "Only task", 1, true);

        as(patchActive(module, task, false), UserRole.ADMIN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MODULE_REQUIRES_ACTIVE_TASK"));

        assertThat(taskRepository.findById(task.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void setActive_deactivateOneOfTwoActiveTasks_deactivates() throws Exception {
        CourseModule module = saveModule(true);
        CodingTask first = saveTask(module, "First", 1, true);
        saveTask(module, "Second", 2, true);

        as(patchActive(module, first, false), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void setActive_deactivateLastActiveTaskOfInactiveModule_deactivates() throws Exception {
        CourseModule module = saveModule(false);
        CodingTask task = saveTask(module, "Task", 1, true);

        as(patchActive(module, task, false), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void setActive_missingFlag_returns400WithFieldError() throws Exception {
        CourseModule module = saveModule(true);
        CodingTask task = saveTask(module, "Task", 1, false);

        as(patch(taskUrl(module.getId(), task.getId()) + "/active").contentType(MediaType.APPLICATION_JSON).content("{}"),
                UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.active").exists());
    }

    @Test
    void setActive_unknownTask_returns404() throws Exception {
        CourseModule module = saveModule(true);

        as(patch(taskUrl(module.getId(), UNKNOWN_ID) + "/active").contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":true}"), UserRole.ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CODING_TASK_NOT_FOUND"));
    }

    // ---------- delete ----------

    @Test
    void delete_removesTheTaskTogetherWithItsCases() throws Exception {
        CourseModule module = saveModule(false);
        CodingTask task = saveTask(module, "Task", 1, false);
        saveCase(task, 1, "1", "1", false);
        saveCase(task, 2, "2", "2", true);
        Long taskId = task.getId();

        as(delete(taskUrl(module.getId(), taskId)), UserRole.ADMIN)
                .andExpect(status().isNoContent());

        assertThat(taskRepository.findById(taskId)).isEmpty();
        assertThat(caseRepository.countByCodingTaskId(taskId)).isZero();
    }

    @Test
    void delete_taskWithResults_returns409AndKeepsEverything() throws Exception {
        CourseModule module = saveModule(false);
        CodingTask task = saveTask(module, "Task", 1, false);
        saveCase(task, 1, "1", "1", false);
        saveResult(task);

        as(delete(taskUrl(module.getId(), task.getId())), UserRole.ADMIN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CODING_TASK_HAS_RESULTS"));

        assertThat(taskRepository.findById(task.getId())).isPresent();
        assertThat(caseRepository.countByCodingTaskId(task.getId())).isEqualTo(1);
    }

    @Test
    void delete_lastActiveTaskOfActiveModule_returns409AndKeepsIt() throws Exception {
        CourseModule module = saveModule(true);
        CodingTask task = saveTask(module, "Only task", 1, true);

        as(delete(taskUrl(module.getId(), task.getId())), UserRole.ADMIN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MODULE_REQUIRES_ACTIVE_TASK"));

        assertThat(taskRepository.findById(task.getId())).isPresent();
    }

    @Test
    void delete_inactiveTaskOfActiveModule_deletes() throws Exception {
        CourseModule module = saveModule(true);
        saveTask(module, "Active task", 1, true);
        CodingTask inactive = saveTask(module, "Draft", 2, false);

        as(delete(taskUrl(module.getId(), inactive.getId())), UserRole.ADMIN)
                .andExpect(status().isNoContent());

        assertThat(taskRepository.findById(inactive.getId())).isEmpty();
    }

    @Test
    void delete_taskThroughAnotherModule_returns404AndKeepsIt() throws Exception {
        CourseModule module = saveModule(false);
        CourseModule other = saveModule(false);
        CodingTask task = saveTask(module, "Task", 1, false);

        as(delete(taskUrl(other.getId(), task.getId())), UserRole.ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CODING_TASK_NOT_FOUND"));

        assertThat(taskRepository.findById(task.getId())).isPresent();
    }

    // ---------- cases ----------

    @Test
    void listCases_returnsCasesInOrderIncludingHiddenOnesAndExpectedOutput() throws Exception {
        CourseModule module = saveModule(true);
        CodingTask task = saveTask(module, "Task", 1, true);
        saveCase(task, 2, "5 5", "10\n", true);
        saveCase(task, 1, "1 2", "3\n", false);

        as(get(casesUrl(module.getId(), task.getId())), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].number").value(1))
                .andExpect(jsonPath("$[0].inputText").value("1 2"))
                .andExpect(jsonPath("$[0].expectedOutput").value("3\n"))
                .andExpect(jsonPath("$[0].hidden").value(false))
                .andExpect(jsonPath("$[1].number").value(2))
                .andExpect(jsonPath("$[1].expectedOutput").value("10\n"))
                .andExpect(jsonPath("$[1].hidden").value(true));
    }

    @Test
    void listCases_unknownTask_returns404() throws Exception {
        CourseModule module = saveModule(true);

        as(get(casesUrl(module.getId(), UNKNOWN_ID)), UserRole.ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CODING_TASK_NOT_FOUND"));
    }

    @Test
    void replaceCases_numbersCasesByPositionAndKeepsOutputUnchanged() throws Exception {
        CourseModule module = saveModule(true);
        CodingTask task = saveTask(module, "Task", 1, false);

        as(put(casesUrl(module.getId(), task.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(casesJson(caseJson("1 2", "3\n", false), caseJson("", "0\n", true))), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].number").value(1))
                .andExpect(jsonPath("$[0].expectedOutput").value("3\n"))
                .andExpect(jsonPath("$[0].hidden").value(false))
                .andExpect(jsonPath("$[1].number").value(2))
                .andExpect(jsonPath("$[1].inputText").value(""))
                .andExpect(jsonPath("$[1].hidden").value(true));

        List<CodingTaskCase> stored = caseRepository.findByCodingTaskIdOrderByOrderNumberAsc(task.getId());
        assertThat(stored).extracting(CodingTaskCase::getOrderNumber).containsExactly(1, 2);
    }

    @Test
    void replaceCases_secondCallReplacesTheFirstWithoutHittingTheUniqueKey() throws Exception {
        CourseModule module = saveModule(true);
        CodingTask task = saveTask(module, "Task", 1, false);

        as(put(casesUrl(module.getId(), task.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(casesJson(caseJson("1", "1", false), caseJson("2", "2", false), caseJson("3", "3", false))),
                UserRole.ADMIN)
                .andExpect(status().isOk());
        as(put(casesUrl(module.getId(), task.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(casesJson(caseJson("9", "9", true), caseJson("8", "8", true))), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        List<CodingTaskCase> stored = caseRepository.findByCodingTaskIdOrderByOrderNumberAsc(task.getId());
        assertThat(stored).extracting(CodingTaskCase::getInputText).containsExactly("9", "8");
        assertThat(stored).extracting(CodingTaskCase::getOrderNumber).containsExactly(1, 2);
    }

    @Test
    void replaceCases_doesNotTouchTheCasesOfAnotherTask() throws Exception {
        CourseModule module = saveModule(true);
        CodingTask task = saveTask(module, "Task", 1, false);
        CodingTask other = saveTask(module, "Other", 2, false);
        saveCase(other, 1, "keep", "keep", false);

        as(put(casesUrl(module.getId(), task.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(casesJson(caseJson("1", "1", false))), UserRole.ADMIN)
                .andExpect(status().isOk());

        assertThat(caseRepository.findByCodingTaskIdOrderByOrderNumberAsc(other.getId()))
                .extracting(CodingTaskCase::getInputText).containsExactly("keep");
    }

    @Test
    void replaceCases_twentyLargeCases_areAcceptedUnderTheLargeBodyLimit() throws Exception {
        CourseModule module = saveModule(true);
        CodingTask task = saveTask(module, "Task", 1, false);
        String large = "a".repeat(10_000);
        String[] cases = Collections.nCopies(20, caseJson(large, large, true)).toArray(new String[0]);

        as(put(casesUrl(module.getId(), task.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(casesJson(cases)), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(20));
    }

    @Test
    void replaceCases_emptyList_returns400AndKeepsTheOldCases() throws Exception {
        CourseModule module = saveModule(true);
        CodingTask task = saveTask(module, "Task", 1, true);
        saveCase(task, 1, "1", "1", false);

        as(put(casesUrl(module.getId(), task.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(casesJson()), UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.cases").exists());

        assertThat(caseRepository.countByCodingTaskId(task.getId())).isEqualTo(1);
    }

    @Test
    void replaceCases_twentyOneCases_returns400() throws Exception {
        CourseModule module = saveModule(true);
        CodingTask task = saveTask(module, "Task", 1, false);
        String[] cases = Collections.nCopies(21, caseJson("1", "1", false)).toArray(new String[0]);

        as(put(casesUrl(module.getId(), task.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(casesJson(cases)), UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.cases").exists());
    }

    @Test
    void replaceCases_missingCasesField_returns400() throws Exception {
        CourseModule module = saveModule(true);
        CodingTask task = saveTask(module, "Task", 1, false);

        as(put(casesUrl(module.getId(), task.getId())).contentType(MediaType.APPLICATION_JSON).content("{}"),
                UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.cases").exists());
    }

    @Test
    void replaceCases_nullElement_returns400() throws Exception {
        CourseModule module = saveModule(true);
        CodingTask task = saveTask(module, "Task", 1, false);

        as(put(casesUrl(module.getId(), task.getId())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"cases\":[null]}"), UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void replaceCases_missingFieldsOfACase_return400WithFieldErrors() throws Exception {
        CourseModule module = saveModule(true);
        CodingTask task = saveTask(module, "Task", 1, false);

        as(put(casesUrl(module.getId(), task.getId())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"cases\":[{}]}"), UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors['cases[0].inputText']").exists())
                .andExpect(jsonPath("$.fieldErrors['cases[0].expectedOutput']").exists())
                .andExpect(jsonPath("$.fieldErrors['cases[0].hidden']").exists());
    }

    @Test
    void replaceCases_fieldTooLong_returns400() throws Exception {
        CourseModule module = saveModule(true);
        CodingTask task = saveTask(module, "Task", 1, false);

        as(put(casesUrl(module.getId(), task.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(casesJson(caseJson("a".repeat(16_385), "b".repeat(16_385), false))), UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['cases[0].inputText']").exists())
                .andExpect(jsonPath("$.fieldErrors['cases[0].expectedOutput']").exists());
    }

    @Test
    void replaceCases_unknownTask_returns404() throws Exception {
        CourseModule module = saveModule(true);

        as(put(casesUrl(module.getId(), UNKNOWN_ID)).contentType(MediaType.APPLICATION_JSON)
                .content(casesJson(caseJson("1", "1", false))), UserRole.ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CODING_TASK_NOT_FOUND"));
    }

    // ---------- helpers ----------

    private ResultActions as(MockHttpServletRequestBuilder request, UserRole role) throws Exception {
        String token = jwtService.generateAccessToken(saveUser(role));
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private MockHttpServletRequestBuilder patchActive(CourseModule module, CodingTask task, boolean active) {
        return patch(taskUrl(module.getId(), task.getId()) + "/active")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":" + active + "}");
    }

    private User saveUser(UserRole role) {
        return testUsers.user(role).save();
    }

    private CourseModule saveModule(boolean active) {
        CourseModule module = new CourseModule();
        module.setTitle("Module for coding tasks");
        module.setOrderNumber(9_001);
        module.setActive(active);
        return moduleRepository.saveAndFlush(module);
    }

    private CodingTask saveTask(CourseModule module, String title, int orderNumber, boolean active) {
        CodingTask task = new CodingTask();
        task.setModule(module);
        task.setTitle(title);
        task.setOrderNumber(orderNumber);
        task.setReferenceSolution("class Solution { }");
        task.setActive(active);
        return taskRepository.saveAndFlush(task);
    }

    private CodingTaskCase saveCase(CodingTask task, int number, String input, String expected, boolean hidden) {
        CodingTaskCase testCase = new CodingTaskCase();
        testCase.setCodingTask(task);
        testCase.setOrderNumber(number);
        testCase.setInputText(input);
        testCase.setExpectedOutput(expected);
        testCase.setHidden(hidden);
        return caseRepository.saveAndFlush(testCase);
    }

    /** A student's stored result; the foreign key from coding_results is what blocks deleting the task. */
    private void saveResult(CodingTask task) {
        CodingResult result = new CodingResult();
        result.setCodingTask(task);
        result.setUser(saveUser(UserRole.STUDENT));
        result.setSubmittedCode("class Solution { }");
        result.setStatus(CodingResultStatus.FAILED);
        entityManager.persist(result);
        entityManager.flush();
    }

    private static String tasksUrl(Long moduleId) {
        return "/api/v1/modules/" + moduleId + "/coding-tasks";
    }

    private static String taskUrl(Long moduleId, Long taskId) {
        return tasksUrl(moduleId) + "/" + taskId;
    }

    private static String casesUrl(Long moduleId, Long taskId) {
        return taskUrl(moduleId, taskId) + "/cases";
    }

    private static String validTaskJson() {
        return taskJson("Sucet", "Spocitaj dve cisla", "class Solution {}", null, "class Solution { }", 1);
    }

    private static String taskJson(String title, String description, String templateCode, String driverCode,
                                   String referenceSolution, Integer orderNumber) {
        return "{\"title\":" + quote(title) + ",\"description\":" + quote(description)
                + ",\"templateCode\":" + quote(templateCode) + ",\"driverCode\":" + quote(driverCode)
                + ",\"referenceSolution\":" + quote(referenceSolution) + ",\"orderNumber\":" + orderNumber + "}";
    }

    private static String caseJson(String input, String expected, Boolean hidden) {
        return "{\"inputText\":" + quote(input) + ",\"expectedOutput\":" + quote(expected) + ",\"hidden\":" + hidden + "}";
    }

    private static String casesJson(String... cases) {
        return "{\"cases\":[" + String.join(",", cases) + "]}";
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }
}
