package com.tgrznar.javalearningapp.learning.progress.api;

import com.tgrznar.javalearningapp.auth.security.JwtService;
import com.tgrznar.javalearningapp.codingresult.CodingResult;
import com.tgrznar.javalearningapp.codingresult.CodingResultRepository;
import com.tgrznar.javalearningapp.codingresult.CodingResultStatus;
import com.tgrznar.javalearningapp.codingtask.CodingTask;
import com.tgrznar.javalearningapp.codingtask.CodingTaskRepository;
import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.module.CourseModuleRepository;
import com.tgrznar.javalearningapp.quizresult.QuizResult;
import com.tgrznar.javalearningapp.quizresult.QuizResultRepository;
import com.tgrznar.javalearningapp.testsupport.EnrolledClassFactory;
import com.tgrznar.javalearningapp.testsupport.TestUsers;
import com.tgrznar.javalearningapp.testsupport.TestcontainersConfiguration;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRole;
import com.tgrznar.javalearningapp.userprogress.UserProgress;
import com.tgrznar.javalearningapp.userprogress.UserProgressRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests of the student's own progress (GET /api/v1/learning/progress, UC-10). They also cover the
 * new repository queries against the real database. Everything the student must never get back carries a
 * SECRET marker and the last test checks that no response contains it.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
class StudentProgressApiIntegrationTest {

    private static final String URL = "/api/v1/learning/progress";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CourseModuleRepository moduleRepository;

    @Autowired
    private CodingTaskRepository taskRepository;

    @Autowired
    private CodingResultRepository codingResultRepository;

    @Autowired
    private QuizResultRepository quizResultRepository;

    @Autowired
    private UserProgressRepository progressRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TestUsers testUsers;

    @Autowired
    private EnrolledClassFactory enrolledClasses;

    // ---------- access control ----------

    @Test
    void get_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void get_asAdminOrTeacher_returns403() throws Exception {
        for (UserRole role : List.of(UserRole.ADMIN, UserRole.TEACHER)) {
            asUser(get(URL), testUsers.user(role).save())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }

    @Test
    void get_asStudentWithoutClass_returns403NotEnrolled() throws Exception {
        asUser(get(URL), testUsers.user(UserRole.STUDENT).save())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ENROLLED"));
    }

    // ---------- modules ----------

    @Test
    void get_newStudent_seesActiveModulesInCourseOrderWithNothingDone() throws Exception {
        CourseModule second = saveModule("Second", 2, true);
        CourseModule first = saveModule("First", 1, true);
        saveModule("Hidden", 3, false);

        asUser(get(URL), enrolledClasses.saveEnrolledStudent())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedModules").value(0))
                .andExpect(jsonPath("$.totalModules").value(2))
                .andExpect(jsonPath("$.modules.length()").value(2))
                .andExpect(jsonPath("$.modules[0].moduleId").value(first.getId()))
                .andExpect(jsonPath("$.modules[1].moduleId").value(second.getId()))
                .andExpect(jsonPath("$.modules[0].status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.modules[0].locked").value(false))
                .andExpect(jsonPath("$.modules[1].locked").value(true))
                .andExpect(jsonPath("$.modules[0].theoryCompleted").value(false))
                .andExpect(jsonPath("$.modules[0].quizCompleted").value(false))
                .andExpect(jsonPath("$.modules[0].codingCompleted").value(false))
                .andExpect(jsonPath("$.modules[0].lastQuiz").value(nullValue()))
                .andExpect(jsonPath("$.modules[0].coding").value(nullValue()))
                .andExpect(jsonPath("$.modules[1].coding").value(nullValue()));
    }

    @Test
    void get_countsCompletedModules() throws Exception {
        CourseModule first = saveModule("First", 1, true);
        CourseModule second = saveModule("Second", 2, true);
        User student = enrolledClasses.saveEnrolledStudent();
        saveProgress(student, first, true, true, true);
        saveProgress(student, second, true, false, false);

        asUser(get(URL), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedModules").value(1))
                .andExpect(jsonPath("$.totalModules").value(2))
                .andExpect(jsonPath("$.modules[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.modules[1].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.modules[1].locked").value(false));
    }

    // ---------- quiz ----------

    @Test
    void get_lastQuizIsTheLatestAttemptNotTheBestOne_andQuizCompletedStays() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        User student = enrolledClasses.saveEnrolledStudent();
        saveProgress(student, module, true, true, false);
        saveQuizResult(student, module, 9, 10);
        saveQuizResult(student, module, 4, 10);

        asUser(get(URL), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules[0].quizCompleted").value(true))
                .andExpect(jsonPath("$.modules[0].lastQuiz.score").value(4))
                .andExpect(jsonPath("$.modules[0].lastQuiz.maxScore").value(10))
                .andExpect(jsonPath("$.modules[0].lastQuiz.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.modules[0].lastQuiz.passed").doesNotExist());
    }

    @Test
    void get_lastQuizIsPerModule() throws Exception {
        CourseModule first = saveModule("First", 1, true);
        CourseModule second = saveModule("Second", 2, true);
        User student = enrolledClasses.saveEnrolledStudent();
        saveProgress(student, first, true, true, false);
        saveProgress(student, second, true, false, false);
        saveQuizResult(student, first, 8, 10);
        saveQuizResult(student, second, 3, 5);

        asUser(get(URL), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules[0].lastQuiz.score").value(8))
                .andExpect(jsonPath("$.modules[1].lastQuiz.score").value(3))
                .andExpect(jsonPath("$.modules[1].lastQuiz.maxScore").value(5));
    }

    // ---------- coding ----------

    @Test
    void get_codingIsNullUntilTheTheoryIsRead_thenListsTheTasks() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        saveTask(module, 1, true);
        User student = enrolledClasses.saveEnrolledStudent();

        asUser(get(URL), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules[0].coding").value(nullValue()));

        saveProgress(student, module, true, false, false);

        asUser(get(URL), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules[0].coding.activeTasks").value(1))
                .andExpect(jsonPath("$.modules[0].coding.tasks.length()").value(1));
    }

    @Test
    void get_derivesTheStateOfEveryTask() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        CodingTask passed = saveTask(module, 1, true);
        CodingTask failed = saveTask(module, 2, true);
        CodingTask untouched = saveTask(module, 3, true);
        User student = enrolledClasses.saveEnrolledStudent();
        saveProgress(student, module, true, false, false);
        saveResult(student, passed, CodingResultStatus.FAILED);
        saveResult(student, passed, CodingResultStatus.PASSED);
        saveResult(student, failed, CodingResultStatus.COMPILE_ERROR);
        saveResult(student, failed, CodingResultStatus.RUNTIME_ERROR);

        asUser(get(URL), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules[0].coding.activeTasks").value(3))
                .andExpect(jsonPath("$.modules[0].coding.passedTasks").value(1))
                .andExpect(jsonPath("$.modules[0].coding.tasks[0].taskId").value(passed.getId()))
                .andExpect(jsonPath("$.modules[0].coding.tasks[0].state").value("PASSED"))
                .andExpect(jsonPath("$.modules[0].coding.tasks[0].lastAttemptAt").isNotEmpty())
                .andExpect(jsonPath("$.modules[0].coding.tasks[1].taskId").value(failed.getId()))
                .andExpect(jsonPath("$.modules[0].coding.tasks[1].state").value("FAILED"))
                .andExpect(jsonPath("$.modules[0].coding.tasks[1].lastAttemptAt").isNotEmpty())
                .andExpect(jsonPath("$.modules[0].coding.tasks[2].taskId").value(untouched.getId()))
                .andExpect(jsonPath("$.modules[0].coding.tasks[2].state").value("NOT_ATTEMPTED"))
                .andExpect(jsonPath("$.modules[0].coding.tasks[2].lastAttemptAt").value(nullValue()));
    }

    @Test
    void get_inactiveTaskIsNeitherListedNorCounted() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        CodingTask active = saveTask(module, 1, true);
        CodingTask inactive = saveTask(module, 2, false);
        User student = enrolledClasses.saveEnrolledStudent();
        saveProgress(student, module, true, false, false);
        saveResult(student, inactive, CodingResultStatus.PASSED);

        asUser(get(URL), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules[0].coding.activeTasks").value(1))
                .andExpect(jsonPath("$.modules[0].coding.passedTasks").value(0))
                .andExpect(jsonPath("$.modules[0].coding.tasks.length()").value(1))
                .andExpect(jsonPath("$.modules[0].coding.tasks[0].taskId").value(active.getId()))
                .andExpect(jsonPath("$.modules[0].coding.tasks[0].state").value("NOT_ATTEMPTED"));
    }

    @Test
    void get_codingCompletedStaysAfterANewTaskIsAdded() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        CodingTask solved = saveTask(module, 1, true);
        saveTask(module, 2, true);
        User student = enrolledClasses.saveEnrolledStudent();
        saveProgress(student, module, true, true, true);
        saveResult(student, solved, CodingResultStatus.PASSED);

        asUser(get(URL), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules[0].codingCompleted").value(true))
                .andExpect(jsonPath("$.modules[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.modules[0].coding.activeTasks").value(2))
                .andExpect(jsonPath("$.modules[0].coding.passedTasks").value(1));
    }

    // ---------- isolation and what must not leak ----------

    @Test
    void get_doesNotShowDataOfAnotherStudent() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        CodingTask task = saveTask(module, 1, true);
        User other = enrolledClasses.saveEnrolledStudent();
        saveProgress(other, module, true, true, true);
        saveQuizResult(other, module, 10, 10);
        saveResult(other, task, CodingResultStatus.PASSED);

        User student = enrolledClasses.saveEnrolledStudent();
        saveProgress(student, module, true, false, false);

        asUser(get(URL), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedModules").value(0))
                .andExpect(jsonPath("$.modules[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.modules[0].quizCompleted").value(false))
                .andExpect(jsonPath("$.modules[0].lastQuiz").value(nullValue()))
                .andExpect(jsonPath("$.modules[0].coding.passedTasks").value(0))
                .andExpect(jsonPath("$.modules[0].coding.tasks[0].state").value("NOT_ATTEMPTED"));
    }

    @Test
    void get_neverReturnsCodeOutputErrorsOrTaskInternals() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        CodingTask task = saveTask(module, 1, true);
        User student = enrolledClasses.saveEnrolledStudent();
        saveProgress(student, module, true, false, false);
        CodingResult result = new CodingResult();
        result.setUser(student);
        result.setCodingTask(task);
        result.setSubmittedCode("SECRET-CODE");
        result.setStatus(CodingResultStatus.FAILED);
        result.setActualOutput("SECRET-OUTPUT");
        result.setErrorMessage("SECRET-ERROR");
        codingResultRepository.saveAndFlush(result);

        String body = asUser(get(URL), student)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("SECRET");
        assertThat(body).doesNotContain("submittedCode", "actualOutput", "errorMessage",
                "driverCode", "referenceSolution", "templateCode", "expectedOutput");
    }

    // ---------- helpers ----------

    private ResultActions asUser(MockHttpServletRequestBuilder request, User user) throws Exception {
        String token = jwtService.generateAccessToken(user);
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private CourseModule saveModule(String title, int orderNumber, boolean active) {
        CourseModule module = new CourseModule();
        module.setTitle(title);
        module.setDescription("Description of " + title);
        module.setTheoryContent("# Theory of " + title);
        module.setOrderNumber(orderNumber);
        module.setActive(active);
        return moduleRepository.saveAndFlush(module);
    }

    private CodingTask saveTask(CourseModule module, int order, boolean active) {
        CodingTask task = new CodingTask();
        task.setModule(module);
        task.setOrderNumber(order);
        task.setTitle("Task " + order);
        task.setDescription("Description");
        task.setTemplateCode("SECRET-TEMPLATE");
        task.setDriverCode("SECRET-DRIVER");
        task.setReferenceSolution("SECRET-REFERENCE");
        task.setActive(active);
        return taskRepository.saveAndFlush(task);
    }

    private void saveProgress(User user, CourseModule module, boolean theory, boolean quiz, boolean coding) {
        UserProgress progress = new UserProgress();
        progress.setUser(user);
        progress.setModule(module);
        progress.setTheoryCompleted(theory);
        progress.setQuizCompleted(quiz);
        progress.setCodingCompleted(coding);
        progressRepository.saveAndFlush(progress);
    }

    private void saveQuizResult(User user, CourseModule module, int score, int maxScore) {
        QuizResult result = new QuizResult();
        result.setUser(user);
        result.setModule(module);
        result.setScore(score);
        result.setMaxScore(maxScore);
        quizResultRepository.saveAndFlush(result);
    }

    private void saveResult(User student, CodingTask task, CodingResultStatus status) {
        CodingResult result = new CodingResult();
        result.setUser(student);
        result.setCodingTask(task);
        result.setSubmittedCode("code");
        result.setStatus(status);
        codingResultRepository.saveAndFlush(result);
    }
}
