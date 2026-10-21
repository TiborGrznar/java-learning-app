package com.tgrznar.javalearningapp.learning.api;

import com.tgrznar.javalearningapp.auth.security.JwtService;
import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.schoolclass.SchoolClass;
import com.tgrznar.javalearningapp.testsupport.EnrolledClassFactory;
import com.tgrznar.javalearningapp.testsupport.TestUsers;
import com.tgrznar.javalearningapp.testsupport.TestcontainersConfiguration;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests of the enrollment gate on /api/v1/learning/**: a student without a class gets
 * 403 NOT_ENROLLED, a student with a class (even a deactivated one) gets through. The role rejections of
 * teachers and admins (ACCESS_DENIED) are covered by the learning tests of each controller, whose
 * teachers and admins have no class, so they also prove the gate does not change those answers.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
class LearningEnrollmentGateApiIntegrationTest {

    private static final String MODULES_URL = "/api/v1/learning/modules";
    private static final String ENROLLMENTS_URL = "/api/v1/enrollments";
    private static final String NOT_ENROLLED = "NOT_ENROLLED";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestUsers testUsers;

    @Autowired
    private EnrolledClassFactory enrolledClasses;

    @Autowired
    private JwtService jwtService;

    // ---------- student without a class ----------

    @Test
    void list_studentWithoutClass_returns403NotEnrolled() throws Exception {
        assertNotEnrolled(get(MODULES_URL));
    }

    @Test
    void list_studentWithSchoolButWithoutClass_returns403NotEnrolled() throws Exception {
        School school = enrolledClasses.saveClass(true).getSchool();

        asUser(get(MODULES_URL), saveStudent(school, null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(NOT_ENROLLED));
    }

    @Test
    void get_studentWithoutClass_returnsNotEnrolledAndNeverLooksTheModuleUp() throws Exception {
        // An unknown module id would otherwise answer 404 MODULE_NOT_FOUND: the gate must come first.
        asUser(get(MODULES_URL + "/999999999"), saveStudent(null, null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(NOT_ENROLLED))
                .andExpect(jsonPath("$.theoryContent").doesNotExist());
    }

    @Test
    void completeTheory_studentWithoutClass_returns403NotEnrolled() throws Exception {
        assertNotEnrolled(post(MODULES_URL + "/1/theory-completed"));
    }

    @Test
    void quizLoad_studentWithoutClass_returns403NotEnrolled() throws Exception {
        assertNotEnrolled(get(MODULES_URL + "/1/quiz"));
    }

    @Test
    void quizSubmit_studentWithoutClass_returns403NotEnrolledEvenForAnInvalidBody() throws Exception {
        assertNotEnrolled(post(MODULES_URL + "/1/quiz/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));
    }

    // ---------- student with a class ----------

    @Test
    void list_studentInActiveClass_returns200() throws Exception {
        SchoolClass schoolClass = enrolledClasses.saveClass(true);

        asUser(get(MODULES_URL), saveStudent(schoolClass.getSchool(), schoolClass))
                .andExpect(status().isOk());
    }

    @Test
    void list_studentInDeactivatedClass_keepsAccess() throws Exception {
        SchoolClass schoolClass = enrolledClasses.saveClass(false);

        asUser(get(MODULES_URL), saveStudent(schoolClass.getSchool(), schoolClass))
                .andExpect(status().isOk());
    }

    // ---------- scope of the gate ----------

    @Test
    void list_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(MODULES_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void enrollment_studentWithoutClass_canStillJoinAClass() throws Exception {
        // The gate covers only /api/v1/learning/**, otherwise a new student could never join a class.
        asUser(post(ENROLLMENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"joinCode\":\"ZZZZZZZZ\"}"),
                saveStudent(null, null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVALID_JOIN_CODE"));
    }

    // ---------- helpers ----------

    private void assertNotEnrolled(MockHttpServletRequestBuilder request) throws Exception {
        asUser(request, saveStudent(null, null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(NOT_ENROLLED));
    }

    private ResultActions asUser(MockHttpServletRequestBuilder request, User user) throws Exception {
        String token = jwtService.generateAccessToken(user);
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private User saveStudent(School school, SchoolClass schoolClass) {
        TestUsers.Builder student = testUsers.user(UserRole.STUDENT).school(school);
        if (schoolClass != null) {
            student.inClass(schoolClass);
        }
        return student.save();
    }
}