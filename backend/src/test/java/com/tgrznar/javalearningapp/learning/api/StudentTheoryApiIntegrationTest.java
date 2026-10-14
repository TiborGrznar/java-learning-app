package com.tgrznar.javalearningapp.learning.api;

import com.tgrznar.javalearningapp.auth.security.JwtService;
import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.module.CourseModuleRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests of marking a module's theory as read
 * (POST /api/v1/learning/modules/{id}/theory-completed).
 * Test data uses unique e-mails and is rolled back after each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StudentTheoryApiIntegrationTest {

    private static final String LIST_URL = "/api/v1/learning/modules";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseModuleRepository moduleRepository;

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
    void complete_withoutToken_returns401() throws Exception {
        mockMvc.perform(post(url(1L)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void complete_asAdmin_returns403() throws Exception {
        CourseModule module = saveModule("First", 1, true);

        as(post(url(module.getId())), UserRole.ADMIN)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void complete_asTeacher_returns403() throws Exception {
        CourseModule module = saveModule("First", 1, true);

        as(post(url(module.getId())), UserRole.TEACHER)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    // ---------- marking the theory ----------

    @Test
    void complete_createsTheProgressRowWithOnlyTheoryDone() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        User student = saveUser(UserRole.STUDENT);

        asUser(post(url(module.getId())), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theoryCompleted").value(true))
                .andExpect(jsonPath("$.quizCompleted").value(false))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        UserProgress saved = progressRepository.findByUserIdAndModuleId(student.getId(), module.getId()).orElseThrow();
        assertThat(saved.isTheoryCompleted()).isTrue();
        assertThat(saved.isQuizCompleted()).isFalse();
        assertThat(saved.isCodingCompleted()).isFalse();
    }

    @Test
    void complete_keepsTheOtherFlagsOfExistingProgress() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        User student = saveUser(UserRole.STUDENT);
        saveProgress(student, module, false, true, false);

        asUser(post(url(module.getId())), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theoryCompleted").value(true))
                .andExpect(jsonPath("$.quizCompleted").value(true))
                .andExpect(jsonPath("$.codingCompleted").value(false));
    }

    @Test
    void complete_calledTwice_isIdempotent() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        User student = saveUser(UserRole.STUDENT);

        asUser(post(url(module.getId())), student).andExpect(status().isOk());
        asUser(post(url(module.getId())), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theoryCompleted").value(true));

        assertThat(progressRepository.findByUserId(student.getId())).hasSize(1);
    }

    @Test
    void complete_lastMissingFlag_makesTheModuleCompleted() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        User student = saveUser(UserRole.STUDENT);
        saveProgress(student, module, false, true, true);

        asUser(post(url(module.getId())), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void complete_doesNotUnlockTheNextModule() throws Exception {
        CourseModule first = saveModule("First", 1, true);
        saveModule("Second", 2, true);
        User student = saveUser(UserRole.STUDENT);

        asUser(post(url(first.getId())), student).andExpect(status().isOk());

        asUser(get(LIST_URL), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].locked").value(true));
    }

    @Test
    void complete_changesOnlyTheProgressOfTheCallingStudent() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        User student = saveUser(UserRole.STUDENT);
        User other = saveUser(UserRole.STUDENT);

        asUser(post(url(module.getId())), student).andExpect(status().isOk());

        assertThat(progressRepository.findByUserId(other.getId())).isEmpty();
    }

    // ---------- locking and missing modules ----------

    @Test
    void complete_lockedModule_returns403AndCreatesNothing() throws Exception {
        saveModule("First", 1, true);
        CourseModule second = saveModule("Second", 2, true);
        User student = saveUser(UserRole.STUDENT);

        asUser(post(url(second.getId())), student)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_LOCKED"));

        assertThat(progressRepository.findByUserIdAndModuleId(student.getId(), second.getId())).isEmpty();
    }

    @Test
    void complete_moduleUnlockedByPassedQuiz_returns200() throws Exception {
        CourseModule first = saveModule("First", 1, true);
        CourseModule second = saveModule("Second", 2, true);
        User student = saveUser(UserRole.STUDENT);
        saveProgress(student, first, false, true, false);

        asUser(post(url(second.getId())), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theoryCompleted").value(true));
    }

    @Test
    void complete_inactiveModule_returns404() throws Exception {
        CourseModule module = saveModule("Hidden", 1, false);

        as(post(url(module.getId())), UserRole.STUDENT)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_FOUND"));
    }

    @Test
    void complete_unknownModule_returns404() throws Exception {
        as(post(url(999_999_999L)), UserRole.STUDENT)
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

    private static String url(Long moduleId) {
        return LIST_URL + "/" + moduleId + "/theory-completed";
    }
}