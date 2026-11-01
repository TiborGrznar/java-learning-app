package com.tgrznar.javalearningapp.learning.coding.api;

import com.tgrznar.javalearningapp.auth.security.JwtService;
import com.tgrznar.javalearningapp.coderunner.CodeRunnerBusyException;
import com.tgrznar.javalearningapp.coderunner.CodeRunnerUnavailableException;
import com.tgrznar.javalearningapp.codingresult.CodingResult;
import com.tgrznar.javalearningapp.codingresult.CodingResultRepository;
import com.tgrznar.javalearningapp.codingresult.CodingResultStatus;
import com.tgrznar.javalearningapp.codingtask.CodingTask;
import com.tgrznar.javalearningapp.codingtask.CodingTaskCase;
import com.tgrznar.javalearningapp.codingtask.CodingTaskCaseRepository;
import com.tgrznar.javalearningapp.codingtask.CodingTaskRepository;
import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.module.CourseModuleRepository;
import com.tgrznar.javalearningapp.testsupport.EnrolledClassFactory;
import com.tgrznar.javalearningapp.testsupport.FakeCodeRunner;
import com.tgrznar.javalearningapp.testsupport.FakeCodeRunnerConfig;
import com.tgrznar.javalearningapp.testsupport.TestUsers;
import com.tgrznar.javalearningapp.testsupport.TestcontainersConfiguration;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRole;
import com.tgrznar.javalearningapp.userprogress.UserProgress;
import com.tgrznar.javalearningapp.userprogress.UserProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests of the student's coding tasks (/api/v1/learning/modules/{id}/coding-tasks). The sandbox
 * is replaced by a {@link FakeCodeRunner}, so the tests decide what the "program" printed.
 * <p>
 * The task has one visible and two hidden cases. Everything the student must never see carries a marker
 * (SECRET-...) and the tests check that no response contains it.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, FakeCodeRunnerConfig.class})
@AutoConfigureMockMvc
@Transactional
class StudentCodingTaskApiIntegrationTest {

    private static final String MODULES_URL = "/api/v1/learning/modules";
    private static final long UNKNOWN_ID = 999_999_999L;
    private static final String CODE = "public class Solution {\n    int square(int x) { return x * x; }\n}\n";

    private static final List<String> SECRET_MARKERS = List.of(
            "SECRET-DRIVER", "SECRET-REFERENCE", "SECRET-HIDDEN-IN-1", "SECRET-HIDDEN-OUT-1",
            "SECRET-HIDDEN-IN-2", "SECRET-HIDDEN-OUT-2");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CourseModuleRepository moduleRepository;

    @Autowired
    private CodingTaskRepository taskRepository;

    @Autowired
    private CodingTaskCaseRepository caseRepository;

    @Autowired
    private CodingResultRepository resultRepository;

    @Autowired
    private UserProgressRepository progressRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TestUsers testUsers;

    @Autowired
    private EnrolledClassFactory enrolledClasses;

    @Autowired
    private FakeCodeRunner runner;

    @BeforeEach
    void resetRunner() {
        runner.reset();
    }

    // ---------- access control ----------

    @Test
    void everyEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(listUrl(1L))).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get(detailUrl(1L, 1L))).andExpect(status().isUnauthorized());
        mockMvc.perform(post(submitUrl(1L, 1L)).contentType(MediaType.APPLICATION_JSON).content(body(CODE)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_asAdminOrTeacher_returns403() throws Exception {
        CourseModule module = saveModule(1, true);
        for (UserRole role : List.of(UserRole.ADMIN, UserRole.TEACHER)) {
            asUser(get(listUrl(module.getId())), testUsers.user(role).save())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }

    @Test
    void detail_asAdminOrTeacher_returns403() throws Exception {
        CourseModule module = saveModule(1, true);
        CodingTask task = saveTask(module, 1, true);
        for (UserRole role : List.of(UserRole.ADMIN, UserRole.TEACHER)) {
            asUser(get(detailUrl(module.getId(), task.getId())), testUsers.user(role).save())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }

    @Test
    void submit_asAdminOrTeacher_returns403_andTheRunnerIsNotCalled() throws Exception {
        CourseModule module = saveModule(1, true);
        CodingTask task = saveTask(module, 1, true);
        for (UserRole role : List.of(UserRole.ADMIN, UserRole.TEACHER)) {
            asUser(submitRequest(module, task, CODE), testUsers.user(role).save())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
        assertThat(runner.calls()).isEmpty();
    }

    @Test
    void everyEndpoint_asStudentWithoutAClass_returns403NotEnrolled() throws Exception {
        CourseModule module = saveModule(1, true);
        CodingTask task = saveTask(module, 1, true);
        User noClass = testUsers.user(UserRole.STUDENT).save();

        asUser(get(listUrl(module.getId())), noClass)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_ENROLLED"));
        asUser(get(detailUrl(module.getId(), task.getId())), noClass)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_ENROLLED"));
        asUser(submitRequest(module, task, CODE), noClass)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_ENROLLED"));
        assertThat(runner.calls()).isEmpty();
    }

    // ---------- module rules ----------

    @Test
    void lockedModule_returns403ModuleLocked_onEveryEndpoint() throws Exception {
        saveModule(1, true);
        CourseModule second = saveModule(2, true);
        CodingTask task = saveTask(second, 1, true);
        User student = enrolledClasses.saveEnrolledStudent();

        asUser(get(listUrl(second.getId())), student)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MODULE_LOCKED"));
        asUser(get(detailUrl(second.getId(), task.getId())), student)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MODULE_LOCKED"));
        asUser(submitRequest(second, task, CODE), student)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MODULE_LOCKED"));
        assertThat(runner.calls()).isEmpty();
    }

    @Test
    void theoryNotCompleted_returns409_onEveryEndpoint() throws Exception {
        CourseModule module = saveModule(1, true);
        CodingTask task = saveTask(module, 1, true);
        User student = enrolledClasses.saveEnrolledStudent();

        asUser(get(listUrl(module.getId())), student)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("THEORY_NOT_COMPLETED"));
        asUser(get(detailUrl(module.getId(), task.getId())), student)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("THEORY_NOT_COMPLETED"));
        asUser(submitRequest(module, task, CODE), student)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("THEORY_NOT_COMPLETED"));
        assertThat(runner.calls()).isEmpty();
    }

    @Test
    void theoryCompletedWithoutTheQuiz_isEnough() throws Exception {
        CourseModule module = saveModule(1, true);
        CodingTask task = saveTask(module, 1, true);
        saveStandardCases(task);
        User student = studentWithTheory(module);

        asUser(get(detailUrl(module.getId(), task.getId())), student).andExpect(status().isOk());
    }

    @Test
    void inactiveOrUnknownModule_returns404() throws Exception {
        CourseModule hidden = saveModule(1, false);
        User student = enrolledClasses.saveEnrolledStudent();

        asUser(get(listUrl(hidden.getId())), student)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MODULE_NOT_FOUND"));
        asUser(get(listUrl(UNKNOWN_ID)), student)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MODULE_NOT_FOUND"));
    }

    // ---------- list and detail ----------

    @Test
    void list_returnsActiveTasksInOrderWithSolvedFlag() throws Exception {
        CourseModule module = saveModule(1, true);
        CodingTask second = saveTask(module, 2, true);
        CodingTask first = saveTask(module, 1, true);
        saveTask(module, 3, false);
        User student = studentWithTheory(module);
        saveResult(student, first, CodingResultStatus.PASSED);
        saveResult(student, second, CodingResultStatus.FAILED);

        asUser(get(listUrl(module.getId())), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(first.getId()))
                .andExpect(jsonPath("$[0].solved").value(true))
                .andExpect(jsonPath("$[1].id").value(second.getId()))
                .andExpect(jsonPath("$[1].solved").value(false));
    }

    @Test
    void list_solvedFlagBelongsToTheStudent() throws Exception {
        CourseModule module = saveModule(1, true);
        CodingTask task = saveTask(module, 1, true);
        User student = studentWithTheory(module);
        User other = studentWithTheory(module);
        saveResult(other, task, CodingResultStatus.PASSED);

        asUser(get(listUrl(module.getId())), student)
                .andExpect(jsonPath("$[0].solved").value(false));
    }

    @Test
    void detail_showsTheAssignmentAndOnlyTheVisibleCase() throws Exception {
        CourseModule module = saveModule(1, true);
        CodingTask task = saveTask(module, 1, true);
        saveStandardCases(task);
        User student = studentWithTheory(module);

        asUser(get(detailUrl(module.getId(), task.getId())), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Square"))
                .andExpect(jsonPath("$.description").value("Square a number"))
                .andExpect(jsonPath("$.templateCode").value("class Solution {}"))
                .andExpect(jsonPath("$.visibleCases", hasSize(1)))
                .andExpect(jsonPath("$.visibleCases[0].number").value(1))
                .andExpect(jsonPath("$.visibleCases[0].input").value("2\n"))
                .andExpect(jsonPath("$.visibleCases[0].expectedOutput").value("4\n"))
                .andExpect(jsonPath("$.hiddenCaseCount").value(2))
                .andExpect(jsonPath("$.solved").value(false))
                .andExpect(jsonPath("$.driverCode").doesNotExist())
                .andExpect(jsonPath("$.referenceSolution").doesNotExist());
    }

    @Test
    void detail_ofAnInactiveUnknownOrForeignTask_returns404() throws Exception {
        CourseModule module = saveModule(1, true);
        CourseModule other = saveModule(2, true);
        CodingTask inactive = saveTask(module, 1, false);
        CodingTask foreign = saveTask(other, 1, true);
        User student = studentWithTheory(module);

        for (Long taskId : List.of(inactive.getId(), foreign.getId(), UNKNOWN_ID)) {
            asUser(get(detailUrl(module.getId(), taskId)), student)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CODING_TASK_NOT_FOUND"));
        }
    }

    // ---------- submitting ----------

    @Test
    void submit_correctSolution_passesAndCompletesTheCodingPartOfTheModule() throws Exception {
        TaskFixture fixture = standardFixture();
        runner.respondWith(FakeCodeRunner.okWith("4\n", "SECRET-HIDDEN-OUT-1\n", "SECRET-HIDDEN-OUT-2\n"));

        asUser(submitRequest(fixture.module, fixture.task, CODE), fixture.student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PASSED"))
                .andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.passedCases").value(3))
                .andExpect(jsonPath("$.totalCases").value(3))
                .andExpect(jsonPath("$.moduleCodingCompleted").value(true))
                .andExpect(jsonPath("$.cases", hasSize(3)));

        List<CodingResult> results = resultRepository.findByUserIdAndCodingTaskId(fixture.student.getId(), fixture.task.getId());
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getStatus()).isEqualTo(CodingResultStatus.PASSED);
        assertThat(results.get(0).getSubmittedCode()).isEqualTo(CODE);
        assertThat(progressRepository.findByUserIdAndModuleId(fixture.student.getId(), fixture.module.getId())
                .orElseThrow().isCodingCompleted()).isTrue();
    }

    @Test
    void submit_sendsTheCodeTheDriverAndTheInputsToTheRunner() throws Exception {
        TaskFixture fixture = standardFixture();
        runner.respondWith(FakeCodeRunner.okWith("4\n", "SECRET-HIDDEN-OUT-1\n", "SECRET-HIDDEN-OUT-2\n"));

        asUser(submitRequest(fixture.module, fixture.task, CODE), fixture.student).andExpect(status().isOk());

        assertThat(runner.calls()).hasSize(1);
        FakeCodeRunner.Call call = runner.calls().get(0);
        assertThat(call.solutionSource()).isEqualTo(CODE);
        assertThat(call.driverSource()).isEqualTo("SECRET-DRIVER");
        assertThat(call.caseInputs()).containsExactly("2\n", "SECRET-HIDDEN-IN-1\n", "SECRET-HIDDEN-IN-2\n");
        assertThat(call.caseInputs()).noneMatch(input -> input.contains("OUT"));
    }

    @Test
    void submit_wrongOutput_failsShowsTheVisibleOutputAndHidesTheHiddenOne() throws Exception {
        TaskFixture fixture = standardFixture();
        runner.respondWith(FakeCodeRunner.okWith("5\n", "WRONG-HIDDEN-PRINT\n", "SECRET-HIDDEN-OUT-2\n"));

        asUser(submitRequest(fixture.module, fixture.task, CODE), fixture.student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.passed").value(false))
                .andExpect(jsonPath("$.passedCases").value(1))
                .andExpect(jsonPath("$.moduleCodingCompleted").value(false))
                .andExpect(jsonPath("$.actualOutput").value("5\n"))
                .andExpect(jsonPath("$.cases[0].verdict").value("FAILED"))
                .andExpect(jsonPath("$.cases[0].actualOutput").value("5\n"))
                .andExpect(jsonPath("$.cases[1].hidden").value(true))
                .andExpect(jsonPath("$.cases[1].verdict").value("FAILED"))
                .andExpect(jsonPath("$.cases[1].actualOutput").doesNotExist())
                .andExpect(jsonPath("$.cases[1].message").doesNotExist())
                .andExpect(jsonPath("$.cases[2].verdict").value("PASSED"));

        CodingResult stored = resultRepository.findByUserIdAndCodingTaskId(fixture.student.getId(), fixture.task.getId()).get(0);
        assertThat(stored.getStatus()).isEqualTo(CodingResultStatus.FAILED);
        assertThat(progressRepository.findByUserIdAndModuleId(fixture.student.getId(), fixture.module.getId())
                .orElseThrow().isCodingCompleted()).isFalse();
    }

    @Test
    void submit_compileError_returnsTheCompilerMessage() throws Exception {
        TaskFixture fixture = standardFixture();
        runner.respondWith(FakeCodeRunner.compileError("Solution.java:3: error: ';' expected"));

        asUser(submitRequest(fixture.module, fixture.task, CODE), fixture.student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPILE_ERROR"))
                .andExpect(jsonPath("$.errorMessage").value("Solution.java:3: error: ';' expected"))
                .andExpect(jsonPath("$.passedCases").value(0))
                .andExpect(jsonPath("$.totalCases").value(3))
                .andExpect(jsonPath("$.cases", hasSize(0)));

        assertThat(resultRepository.findByUserIdAndCodingTaskId(fixture.student.getId(), fixture.task.getId()))
                .extracting(CodingResult::getStatus).containsExactly(CodingResultStatus.COMPILE_ERROR);
    }

    @Test
    void submit_exceptionInAVisibleCase_showsTheWholeMessage() throws Exception {
        TaskFixture fixture = standardFixture();
        String stderr = "Exception in thread \"main\" java.lang.ArrayIndexOutOfBoundsException: Index 3 out of bounds for length 3\n"
                + "\tat Solution.square(Solution.java:2)";
        runner.respondWith(FakeCodeRunner.compiled(List.of(
                FakeCodeRunner.crashed(1, stderr),
                FakeCodeRunner.ok(2, "SECRET-HIDDEN-OUT-1\n"),
                FakeCodeRunner.ok(3, "SECRET-HIDDEN-OUT-2\n"))));

        asUser(submitRequest(fixture.module, fixture.task, CODE), fixture.student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNTIME_ERROR"))
                .andExpect(jsonPath("$.errorMessage").value(stderr))
                .andExpect(jsonPath("$.cases[0].message").value(stderr));
    }

    @Test
    void submit_exceptionInAHiddenCase_showsOnlyTheTypeAndTheLine() throws Exception {
        TaskFixture fixture = standardFixture();
        String stderr = "Exception in thread \"main\" java.lang.NumberFormatException: For input string: \"SECRET-HIDDEN-IN-1\"\n"
                + "\tat Solution.square(Solution.java:2)";
        runner.respondWith(FakeCodeRunner.compiled(List.of(
                FakeCodeRunner.ok(1, "4\n"),
                FakeCodeRunner.crashed(2, stderr),
                FakeCodeRunner.ok(3, "SECRET-HIDDEN-OUT-2\n"))));

        MvcResult result = asUser(submitRequest(fixture.module, fixture.task, CODE), fixture.student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNTIME_ERROR"))
                .andExpect(jsonPath("$.errorMessage").value("Výnimka NumberFormatException (Solution.java, riadok 2)"))
                .andExpect(jsonPath("$.cases[1].message").value("Výnimka NumberFormatException (Solution.java, riadok 2)"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("SECRET-HIDDEN-IN-1");
    }

    @Test
    void submit_moduleWithTwoTasks_isCompletedOnlyAfterBothArePassed() throws Exception {
        CourseModule module = saveModule(1, true);
        CodingTask first = saveTask(module, 1, true);
        CodingTask second = saveTask(module, 2, true);
        saveCase(first, 1, "1\n", "1\n", false);
        saveCase(second, 1, "2\n", "2\n", false);
        User student = studentWithTheory(module);

        runner.respondWith(FakeCodeRunner.okWith("1\n"));
        asUser(submitRequest(module, first, CODE), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.moduleCodingCompleted").value(false));

        runner.respondWith(FakeCodeRunner.okWith("2\n"));
        asUser(submitRequest(module, second, CODE), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moduleCodingCompleted").value(true));
    }

    @Test
    void submit_laterFailure_doesNotTakeTheCompletionBack() throws Exception {
        TaskFixture fixture = standardFixture();
        runner.respondWith(FakeCodeRunner.okWith("4\n", "SECRET-HIDDEN-OUT-1\n", "SECRET-HIDDEN-OUT-2\n"));
        asUser(submitRequest(fixture.module, fixture.task, CODE), fixture.student).andExpect(status().isOk());

        runner.respondWith(FakeCodeRunner.okWith("5\n", "x\n", "y\n"));
        asUser(submitRequest(fixture.module, fixture.task, CODE), fixture.student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(false))
                .andExpect(jsonPath("$.moduleCodingCompleted").value(true));

        assertThat(progressRepository.findByUserIdAndModuleId(fixture.student.getId(), fixture.module.getId())
                .orElseThrow().isCodingCompleted()).isTrue();
        assertThat(resultRepository.findByUserIdAndCodingTaskId(fixture.student.getId(), fixture.task.getId())).hasSize(2);
    }

    @Test
    void submit_toAnInactiveUnknownOrForeignTask_returns404_andTheRunnerIsNotCalled() throws Exception {
        CourseModule module = saveModule(1, true);
        CourseModule other = saveModule(2, true);
        CodingTask inactive = saveTask(module, 1, false);
        CodingTask foreign = saveTask(other, 1, true);
        User student = studentWithTheory(module);

        for (Long taskId : List.of(inactive.getId(), foreign.getId(), UNKNOWN_ID)) {
            asUser(post(submitUrl(module.getId(), taskId)).contentType(MediaType.APPLICATION_JSON).content(body(CODE)), student)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CODING_TASK_NOT_FOUND"));
        }
        assertThat(runner.calls()).isEmpty();
    }

    // ---------- validation ----------

    @Test
    void submit_blankCode_returns400() throws Exception {
        TaskFixture fixture = standardFixture();

        asUser(submitRequest(fixture.module, fixture.task, "   "), fixture.student)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.code").exists());
        assertThat(runner.calls()).isEmpty();
    }

    @Test
    void submit_codeOverTheLimit_returns400() throws Exception {
        TaskFixture fixture = standardFixture();

        asUser(submitRequest(fixture.module, fixture.task, "a".repeat(12_001)), fixture.student)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(runner.calls()).isEmpty();
    }

    @Test
    void submit_codeAtTheLimit_isAccepted() throws Exception {
        TaskFixture fixture = standardFixture();
        runner.respondWith(FakeCodeRunner.okWith("4\n", "SECRET-HIDDEN-OUT-1\n", "SECRET-HIDDEN-OUT-2\n"));

        asUser(submitRequest(fixture.module, fixture.task, "a".repeat(12_000)), fixture.student)
                .andExpect(status().isOk());
    }

    @Test
    void submit_withoutABody_returns400() throws Exception {
        TaskFixture fixture = standardFixture();

        asUser(post(submitUrl(fixture.module.getId(), fixture.task.getId())).contentType(MediaType.APPLICATION_JSON), fixture.student)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
    }

    // ---------- the runner fails ----------

    @Test
    void submit_busyRunner_returns503WithRetryAfter_andStoresNothing() throws Exception {
        TaskFixture fixture = standardFixture();
        runner.failWith(new CodeRunnerBusyException(7));

        asUser(submitRequest(fixture.module, fixture.task, CODE), fixture.student)
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "7"))
                .andExpect(jsonPath("$.code").value("CODE_RUNNER_BUSY"));

        assertThat(resultRepository.findByUserIdAndCodingTaskId(fixture.student.getId(), fixture.task.getId())).isEmpty();
    }

    @Test
    void submit_unavailableRunner_returns500_storesNothing_andTheStudentCanTryAgain() throws Exception {
        TaskFixture fixture = standardFixture();
        runner.failWith(new CodeRunnerUnavailableException("The code runner answered HTTP 401"));

        MvcResult result = asUser(submitRequest(fixture.module, fixture.task, CODE), fixture.student)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("CODE_RUNNER_UNAVAILABLE"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("401");
        assertThat(resultRepository.findByUserIdAndCodingTaskId(fixture.student.getId(), fixture.task.getId())).isEmpty();

        runner.respondWith(FakeCodeRunner.okWith("4\n", "SECRET-HIDDEN-OUT-1\n", "SECRET-HIDDEN-OUT-2\n"));
        asUser(submitRequest(fixture.module, fixture.task, CODE), fixture.student).andExpect(status().isOk());
    }

    // ---------- nothing secret leaves the backend ----------

    @Test
    void noResponse_containsAnythingTheStudentMustNotSee() throws Exception {
        TaskFixture fixture = standardFixture();
        StringBuilder everything = new StringBuilder();

        everything.append(read(asUser(get(listUrl(fixture.module.getId())), fixture.student)));
        everything.append(read(asUser(get(detailUrl(fixture.module.getId(), fixture.task.getId())), fixture.student)));

        runner.respondWith(FakeCodeRunner.okWith("4\n", "SECRET-HIDDEN-OUT-1\n", "SECRET-HIDDEN-OUT-2\n"));
        everything.append(read(asUser(submitRequest(fixture.module, fixture.task, CODE), fixture.student)));

        runner.respondWith(FakeCodeRunner.okWith("5\n", "no\n", "no\n"));
        everything.append(read(asUser(submitRequest(fixture.module, fixture.task, CODE), fixture.student)));

        runner.respondWith(FakeCodeRunner.compileError("Solution.java:1: error"));
        everything.append(read(asUser(submitRequest(fixture.module, fixture.task, CODE), fixture.student)));

        runner.respondWith(FakeCodeRunner.compiled(List.of(
                FakeCodeRunner.ok(1, "4\n"),
                FakeCodeRunner.crashed(2, "Exception in thread \"main\" java.lang.IllegalStateException: SECRET-HIDDEN-IN-1\n\tat Solution.f(Solution.java:1)"),
                FakeCodeRunner.crashed(3, "boom SECRET-HIDDEN-IN-2"))));
        everything.append(read(asUser(submitRequest(fixture.module, fixture.task, CODE), fixture.student)));

        String all = everything.toString();
        for (String marker : SECRET_MARKERS) {
            assertThat(all).as("marker %s must not appear in any student response", marker).doesNotContain(marker);
        }
        assertThat(all).doesNotContain("referenceSolution").doesNotContain("driverCode");
    }

    // ---------- helpers ----------

    private record TaskFixture(CourseModule module, CodingTask task, User student) {
    }

    /** An active module with one active task of three cases (one visible, two hidden) and a student who read the theory. */
    private TaskFixture standardFixture() {
        CourseModule module = saveModule(1, true);
        CodingTask task = saveTask(module, 1, true);
        saveStandardCases(task);
        return new TaskFixture(module, task, studentWithTheory(module));
    }

    private void saveStandardCases(CodingTask task) {
        saveCase(task, 1, "2\n", "4\n", false);
        saveCase(task, 2, "SECRET-HIDDEN-IN-1\n", "SECRET-HIDDEN-OUT-1\n", true);
        saveCase(task, 3, "SECRET-HIDDEN-IN-2\n", "SECRET-HIDDEN-OUT-2\n", true);
    }

    private static String read(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }

    private ResultActions asUser(MockHttpServletRequestBuilder request, User user) throws Exception {
        String token = jwtService.generateAccessToken(user);
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private MockHttpServletRequestBuilder submitRequest(CourseModule module, CodingTask task, String code) {
        return post(submitUrl(module.getId(), task.getId())).contentType(MediaType.APPLICATION_JSON).content(body(code));
    }

    private User studentWithTheory(CourseModule module) {
        User student = enrolledClasses.saveEnrolledStudent();
        UserProgress progress = new UserProgress();
        progress.setUser(student);
        progress.setModule(module);
        progress.setTheoryCompleted(true);
        progressRepository.saveAndFlush(progress);
        return student;
    }

    private CourseModule saveModule(int orderNumber, boolean active) {
        CourseModule module = new CourseModule();
        module.setTitle("Module " + orderNumber);
        module.setDescription("Description");
        module.setTheoryContent("# Theory");
        module.setOrderNumber(orderNumber);
        module.setActive(active);
        return moduleRepository.saveAndFlush(module);
    }

    private CodingTask saveTask(CourseModule module, int order, boolean active) {
        CodingTask task = new CodingTask();
        task.setModule(module);
        task.setOrderNumber(order);
        task.setTitle(order == 1 ? "Square" : "Task " + order);
        task.setDescription("Square a number");
        task.setTemplateCode("class Solution {}");
        task.setDriverCode("SECRET-DRIVER");
        task.setReferenceSolution("SECRET-REFERENCE");
        task.setActive(active);
        return taskRepository.saveAndFlush(task);
    }

    private void saveCase(CodingTask task, int number, String input, String expected, boolean hidden) {
        CodingTaskCase testCase = new CodingTaskCase();
        testCase.setCodingTask(task);
        testCase.setOrderNumber(number);
        testCase.setInputText(input);
        testCase.setExpectedOutput(expected);
        testCase.setHidden(hidden);
        caseRepository.saveAndFlush(testCase);
    }

    private void saveResult(User student, CodingTask task, CodingResultStatus status) {
        CodingResult result = new CodingResult();
        result.setUser(student);
        result.setCodingTask(task);
        result.setSubmittedCode("code");
        result.setStatus(status);
        resultRepository.saveAndFlush(result);
    }

    private static String body(String code) {
        return "{\"code\":\"" + code.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"}";
    }

    private static String listUrl(Long moduleId) {
        return MODULES_URL + "/" + moduleId + "/coding-tasks";
    }

    private static String detailUrl(Long moduleId, Long taskId) {
        return listUrl(moduleId) + "/" + taskId;
    }

    private static String submitUrl(Long moduleId, Long taskId) {
        return detailUrl(moduleId, taskId) + "/submissions";
    }
}
