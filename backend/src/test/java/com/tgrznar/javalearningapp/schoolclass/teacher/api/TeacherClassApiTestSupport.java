package com.tgrznar.javalearningapp.schoolclass.teacher.api;

import com.tgrznar.javalearningapp.auth.security.JwtService;
import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.school.SchoolRepository;
import com.tgrznar.javalearningapp.schoolclass.SchoolClass;
import com.tgrznar.javalearningapp.schoolclass.SchoolClassRepository;
import com.tgrznar.javalearningapp.testsupport.TestUsers;
import com.tgrznar.javalearningapp.testsupport.TestcontainersConfiguration;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared setup and helpers of the teacher class API integration tests.
 * Every test class extends it, so they all share one Spring context. E-mails, school names and class names
 * are unique per test (UUID) and each test is rolled back. The helpers repeat a few of those in
 * AdminClassApiTestSupport, which is package-private in another package.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
abstract class TeacherClassApiTestSupport {

    protected static final String URL = "/api/v1/teacher/classes";
    protected static final long UNKNOWN_ID = 999_999_999L;
    /** Eight characters from the join code alphabet: A-Z without I and O, digits 2-9. */
    protected static final String JOIN_CODE_PATTERN = "[A-HJ-NP-Z2-9]{8}";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected SchoolRepository schoolRepository;

    @Autowired
    protected SchoolClassRepository classRepository;

    @Autowired
    protected TestUsers testUsers;

    @Autowired
    protected JwtService jwtService;

    /** Performs the request with a token of the given, already saved user. */
    protected ResultActions asUser(MockHttpServletRequestBuilder request, User actor) throws Exception {
        String token = jwtService.generateAccessToken(actor);
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    /** Performs the request with a token of a freshly created user of the given role. */
    protected ResultActions asRole(MockHttpServletRequestBuilder request, UserRole role) throws Exception {
        return asUser(request, saveUser(role, null, true));
    }

    /** Checks that admins and students get 403 and an anonymous caller gets 401. */
    protected void assertOnlyTeacherAllowed(Supplier<MockHttpServletRequestBuilder> request) throws Exception {
        for (UserRole role : new UserRole[]{UserRole.ADMIN, UserRole.STUDENT}) {
            asRole(request.get(), role)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code", equalTo("ACCESS_DENIED")));
        }
        mockMvc.perform(request.get())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", equalTo("UNAUTHENTICATED")));
    }

    protected User saveUser(UserRole role, School school, boolean active) {
        return testUsers.user(role)
                .name("Jana")
                .surname("Novakova")
                .school(school)
                .active(active)
                .save();
    }

    protected User saveTeacher(School school) {
        return saveUser(UserRole.TEACHER, school, true);
    }

    protected School saveSchool() {
        School school = new School();
        school.setName("Test school " + UUID.randomUUID());
        school.setAddress("Test street 1");
        school.setActive(true);
        return schoolRepository.saveAndFlush(school);
    }

    protected SchoolClass saveClass(School school, User teacher, String name, boolean active) {
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setSchool(school);
        schoolClass.setTeacher(teacher);
        schoolClass.setName(name);
        schoolClass.setJoinCode(UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT));
        schoolClass.setActive(active);
        return classRepository.saveAndFlush(schoolClass);
    }

    protected static String url(Long id) {
        return URL + "/" + id;
    }

    protected static String joinCodeUrl(Long id) {
        return URL + "/" + id + "/join-code";
    }
}