package com.tgrznar.javalearningapp.schoolclass.enrollment.api;

import com.tgrznar.javalearningapp.auth.security.JwtService;
import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.school.SchoolRepository;
import com.tgrznar.javalearningapp.schoolclass.SchoolClass;
import com.tgrznar.javalearningapp.schoolclass.SchoolClassRepository;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared setup and helpers of the enrollment API integration tests.
 * Every test class extends it, so they all share one Spring context. Each test uses its own users, so the
 * in-memory attempt limiter (a singleton of that context) never mixes up two tests. E-mails, school names and
 * join codes are unique per test (UUID) and each test is rolled back. The helpers repeat a few of those in
 * the other API test support classes, which are package-private in other packages.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
abstract class EnrollmentApiTestSupport {

    protected static final String URL = "/api/v1/enrollments";
    /** A code from the join code alphabet that no real class has (test codes are hexadecimal, no Z). */
    protected static final String UNKNOWN_CODE = "ZZZZZZZZ";
    protected static final String INVALID_CODE_MESSAGE = "Kód triedy je neplatný";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected SchoolRepository schoolRepository;

    @Autowired
    protected SchoolClassRepository classRepository;

    @Autowired
    protected JwtService jwtService;

    /** Performs the request with a token of the given, already saved user. */
    protected ResultActions asUser(MockHttpServletRequestBuilder request, User actor) throws Exception {
        String token = jwtService.generateAccessToken(actor);
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    /** The student sends the code to the enrollment endpoint. */
    protected ResultActions enroll(User student, String code) throws Exception {
        return asUser(json(post(URL), enrollBody(code)), student);
    }

    /**
     * Checks that teachers and admins get 403 and an anonymous caller gets 401. The request needs a valid
     * body, because body validation runs before the role check.
     */
    protected void assertOnlyStudentAllowed(Supplier<MockHttpServletRequestBuilder> request) throws Exception {
        for (UserRole role : new UserRole[]{UserRole.TEACHER, UserRole.ADMIN}) {
            asUser(request.get(), saveUser(role, null, true))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code", equalTo("ACCESS_DENIED")));
        }
        mockMvc.perform(request.get())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", equalTo("UNAUTHENTICATED")));
    }

    protected User saveUser(UserRole role, School school, boolean active) {
        User user = new User();
        user.setName("Jano");
        user.setSurname("Novak");
        user.setEmail("user-" + UUID.randomUUID() + "@example.com");
        // Not a valid BCrypt hash: these users never log in, they only get a token.
        user.setPasswordHash("not-a-real-hash");
        user.setRole(role);
        user.setSchool(school);
        user.setActive(active);
        return userRepository.saveAndFlush(user);
    }

    /** A student who is not in a class yet; the school may be null (a fresh registration). */
    protected User saveStudent(School school) {
        return saveUser(UserRole.STUDENT, school, true);
    }

    protected User saveTeacher(School school) {
        return saveUser(UserRole.TEACHER, school, true);
    }

    protected School saveSchool(boolean active) {
        School school = new School();
        school.setName("Test school " + UUID.randomUUID());
        school.setAddress("Test street 1");
        school.setActive(active);
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

    protected static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected static String enrollBody(String code) {
        return "{\"joinCode\":" + (code == null ? "null" : "\"" + code + "\"") + "}";
    }

    protected static String teacherJoinCodeUrl(Long classId) {
        return "/api/v1/teacher/classes/" + classId + "/join-code";
    }
}