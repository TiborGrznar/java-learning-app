package com.tgrznar.javalearningapp.learning.api;

import com.tgrznar.javalearningapp.auth.security.JwtService;
import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.module.CourseModuleRepository;
import com.tgrznar.javalearningapp.quizquestion.QuizOption;
import com.tgrznar.javalearningapp.quizquestion.QuizQuestion;
import com.tgrznar.javalearningapp.quizquestion.QuizQuestionRepository;
import com.tgrznar.javalearningapp.school.SchoolRepository;
import com.tgrznar.javalearningapp.schoolclass.SchoolClass;
import com.tgrznar.javalearningapp.schoolclass.SchoolClassRepository;
import com.tgrznar.javalearningapp.testsupport.EnrolledClassFactory;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.user.model.UserRole;
import com.tgrznar.javalearningapp.userprogress.UserProgress;
import com.tgrznar.javalearningapp.userprogress.UserProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests of the student's module browsing and locking (/api/v1/learning/modules).
 * Test data uses unique e-mails and is rolled back after each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StudentModuleApiIntegrationTest {

    private static final String URL = "/api/v1/learning/modules";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseModuleRepository moduleRepository;

    @Autowired
    private QuizQuestionRepository questionRepository;

    @Autowired
    private UserProgressRepository progressRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private SchoolClassRepository classRepository;

    /**
     * Modules already present in the development database would take part in the course order
     * and change which modules are locked, so they are switched off inside the test transaction,
     * which is rolled back at the end.
     */
    @BeforeEach
    void deactivateExistingModules() {
        moduleRepository.findAll().forEach(module -> module.setActive(false));
        moduleRepository.flush();
    }

    // ---------- access control ----------

    @Test
    void list_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void list_asAdmin_returns403() throws Exception {
        as(get(URL), UserRole.ADMIN)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void list_asTeacher_returns403() throws Exception {
        as(get(URL), UserRole.TEACHER)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void get_asAdmin_returns403() throws Exception {
        CourseModule module = saveModule("Module", 1, true);

        as(get(url(module.getId())), UserRole.ADMIN)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void get_asTeacher_returns403() throws Exception {
        CourseModule module = saveModule("Module", 1, true);

        as(get(url(module.getId())), UserRole.TEACHER)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    // ---------- list ----------

    @Test
    void list_returnsOnlyActiveModulesInCourseOrderWithoutTheory() throws Exception {
        CourseModule second = saveModule("Second", 2, true);
        CourseModule first = saveModule("First", 1, true);
        saveModule("Hidden", 3, false);

        as(get(URL), UserRole.STUDENT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(first.getId()))
                .andExpect(jsonPath("$[1].id").value(second.getId()))
                .andExpect(jsonPath("$[0].title").value("First"))
                .andExpect(jsonPath("$[0].theoryContent").doesNotExist());
    }

    @Test
    void list_firstModuleIsUnlockedAndTheOthersAreLocked() throws Exception {
        saveModule("First", 1, true);
        saveModule("Second", 2, true);
        saveModule("Third", 3, true);

        as(get(URL), UserRole.STUDENT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].locked").value(false))
                .andExpect(jsonPath("$[1].locked").value(true))
                .andExpect(jsonPath("$[2].locked").value(true))
                .andExpect(jsonPath("$[0].status").value("NOT_STARTED"))
                .andExpect(jsonPath("$[1].status").value("NOT_STARTED"));
    }

    @Test
    void list_passedQuizInTheFirstModuleUnlocksOnlyTheSecond() throws Exception {
        CourseModule first = saveModule("First", 1, true);
        saveModule("Second", 2, true);
        saveModule("Third", 3, true);
        User student = saveUser(UserRole.STUDENT);
        saveProgress(student, first, false, true, false);

        asUser(get(URL), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$[1].locked").value(false))
                .andExpect(jsonPath("$[2].locked").value(true));
    }

    @Test
    void list_theoryOnlyInTheFirstModuleDoesNotUnlockTheSecond() throws Exception {
        CourseModule first = saveModule("First", 1, true);
        saveModule("Second", 2, true);
        User student = saveUser(UserRole.STUDENT);
        saveProgress(student, first, true, false, false);

        asUser(get(URL), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].locked").value(true));
    }

    @Test
    void list_moduleWithOwnProgressStaysUnlocked() throws Exception {
        saveModule("First", 1, true);
        saveModule("Second", 2, true);
        CourseModule third = saveModule("Third", 3, true);
        User student = saveUser(UserRole.STUDENT);
        saveProgress(student, third, true, false, false);

        asUser(get(URL), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].locked").value(true))
                .andExpect(jsonPath("$[2].locked").value(false));
    }

    @Test
    void list_allFlagsSetMeansCompleted() throws Exception {
        CourseModule first = saveModule("First", 1, true);
        User student = saveUser(UserRole.STUDENT);
        saveProgress(student, first, true, true, true);

        asUser(get(URL), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }

    @Test
    void list_inactiveModuleInTheMiddleIsSkippedWhenUnlocking() throws Exception {
        CourseModule first = saveModule("First", 1, true);
        saveModule("Hidden", 2, false);
        CourseModule third = saveModule("Third", 3, true);
        User student = saveUser(UserRole.STUDENT);
        saveProgress(student, first, false, true, false);

        asUser(get(URL), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].id").value(third.getId()))
                .andExpect(jsonPath("$[1].locked").value(false));
    }

    @Test
    void list_progressOfAnotherStudentDoesNotUnlockAnything() throws Exception {
        CourseModule first = saveModule("First", 1, true);
        saveModule("Second", 2, true);
        saveProgress(saveUser(UserRole.STUDENT), first, true, true, true);

        as(get(URL), UserRole.STUDENT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("NOT_STARTED"))
                .andExpect(jsonPath("$[1].locked").value(true));
    }

    // ---------- detail ----------

    @Test
    void get_returnsTheoryProgressAndQuestionCount() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        saveQuestion(module);
        saveQuestion(module);
        User student = saveUser(UserRole.STUDENT);
        saveProgress(student, module, true, false, false);

        asUser(get(url(module.getId())), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(module.getId()))
                .andExpect(jsonPath("$.title").value("First"))
                .andExpect(jsonPath("$.theoryContent").value("# Theory of First"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.theoryCompleted").value(true))
                .andExpect(jsonPath("$.quizCompleted").value(false))
                .andExpect(jsonPath("$.codingCompleted").value(false))
                .andExpect(jsonPath("$.quizQuestionCount").value(2));
    }

    @Test
    void get_withoutProgress_returnsNotStartedWithAllFlagsOff() throws Exception {
        CourseModule module = saveModule("First", 1, true);

        as(get(url(module.getId())), UserRole.STUDENT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.theoryCompleted").value(false))
                .andExpect(jsonPath("$.quizCompleted").value(false))
                .andExpect(jsonPath("$.codingCompleted").value(false))
                .andExpect(jsonPath("$.quizQuestionCount").value(0));
    }

    @Test
    void get_lockedModule_returns403WithoutTheory() throws Exception {
        saveModule("First", 1, true);
        CourseModule second = saveModule("Second", 2, true);

        as(get(url(second.getId())), UserRole.STUDENT)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_LOCKED"))
                .andExpect(jsonPath("$.theoryContent").doesNotExist());
    }

    @Test
    void get_moduleUnlockedByPassedQuiz_returns200() throws Exception {
        CourseModule first = saveModule("First", 1, true);
        CourseModule second = saveModule("Second", 2, true);
        User student = saveUser(UserRole.STUDENT);
        saveProgress(student, first, false, true, false);

        asUser(get(url(second.getId())), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theoryContent").value("# Theory of Second"));
    }

    @Test
    void get_moduleWithOwnProgress_isReturnedEvenIfThePreviousQuizIsNotPassed() throws Exception {
        saveModule("First", 1, true);
        CourseModule second = saveModule("Second", 2, true);
        User student = saveUser(UserRole.STUDENT);
        saveProgress(student, second, true, false, false);

        asUser(get(url(second.getId())), student)
                .andExpect(status().isOk());
    }

    @Test
    void get_inactiveModule_returns404() throws Exception {
        CourseModule module = saveModule("Hidden", 1, false);

        as(get(url(module.getId())), UserRole.STUDENT)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_FOUND"));
    }

    @Test
    void get_unknownModule_returns404() throws Exception {
        as(get(url(999_999_999L)), UserRole.STUDENT)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_FOUND"));
    }

    // ---------- helpers ----------

    private ResultActions as(MockHttpServletRequestBuilder request, UserRole role) throws Exception {
        return asUser(request, saveUser(role));
    }

    private ResultActions asUser(MockHttpServletRequestBuilder request, User user) throws Exception {
        String token = jwtService.generateAccessToken(user);
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private User saveUser(UserRole role) {
        User user = new User();
        user.setName("Jano");
        user.setSurname("Novak");
        user.setEmail(("user-" + UUID.randomUUID() + "@example.com").toLowerCase(Locale.ROOT));
        // Not a valid BCrypt hash: these users never log in, they only get a token.
        user.setPasswordHash("not-a-real-hash");
        user.setRole(role);
        if (role == UserRole.STUDENT) {
            // Learning endpoints are open only to students who joined a class.
            SchoolClass schoolClass = new EnrolledClassFactory(userRepository, schoolRepository, classRepository)
                    .saveClass(true);
            user.setSchool(schoolClass.getSchool());
            user.setSchoolClass(schoolClass);
        }
        return userRepository.saveAndFlush(user);
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

    private void saveProgress(User user, CourseModule module, boolean theory, boolean quiz, boolean coding) {
        UserProgress progress = new UserProgress();
        progress.setUser(user);
        progress.setModule(module);
        progress.setTheoryCompleted(theory);
        progress.setQuizCompleted(quiz);
        progress.setCodingCompleted(coding);
        progressRepository.saveAndFlush(progress);
    }

    private void saveQuestion(CourseModule module) {
        QuizQuestion question = new QuizQuestion();
        question.setModule(module);
        question.setQuestionText("Question");
        question.setOptionA("Option A");
        question.setOptionB("Option B");
        question.setOptionC("Option C");
        question.setOptionD("Option D");
        question.setCorrectOption(QuizOption.B);
        questionRepository.saveAndFlush(question);
    }

    private static String url(Long id) {
        return URL + "/" + id;
    }
}