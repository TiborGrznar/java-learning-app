package com.tgrznar.javalearningapp.user.teacher.api;

import com.jayway.jsonpath.JsonPath;
import com.tgrznar.javalearningapp.auth.security.JwtService;
import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.module.CourseModuleRepository;
import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.school.SchoolRepository;
import com.tgrznar.javalearningapp.schoolclass.SchoolClass;
import com.tgrznar.javalearningapp.schoolclass.SchoolClassRepository;
import com.tgrznar.javalearningapp.testsupport.TestUsers;
import com.tgrznar.javalearningapp.testsupport.TestcontainersConfiguration;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.user.model.UserRole;
import com.tgrznar.javalearningapp.userprogress.UserProgress;
import com.tgrznar.javalearningapp.userprogress.UserProgressRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared setup and helpers of the teacher-student API tests (overview, detail, bulk change). Every test class
 * extends it, so they all share one Spring context. Each test starts with a school, a teacher of that school
 * and one active class of that teacher ({@link #ownClass}); everything is unique per test (UUID) and rolled back.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
abstract class TeacherStudentApiTestSupport {

    protected static final String URL = "/api/v1/teacher/students";
    protected static final long UNKNOWN_ID = 999_999_999L;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected EntityManager entityManager;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected SchoolRepository schoolRepository;

    @Autowired
    protected SchoolClassRepository classRepository;

    @Autowired
    protected CourseModuleRepository moduleRepository;

    @Autowired
    protected UserProgressRepository progressRepository;

    @Autowired
    protected TestUsers testUsers;

    @Autowired
    protected JwtService jwtService;

    protected School school;
    protected User teacher;
    protected SchoolClass ownClass;

    @BeforeEach
    void setUpTeacherWithOneClass() {
        school = saveSchool();
        teacher = saveTeacher(school);
        ownClass = saveClass(school, teacher, "1.A", true);
    }

    /** Performs the request with a token of the given, already saved user. */
    protected ResultActions asUser(MockHttpServletRequestBuilder request, User actor) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + jwtService.generateAccessToken(actor)));
    }

    /** Performs the request with a token of a freshly created user of the given role. */
    protected ResultActions asRole(MockHttpServletRequestBuilder request, UserRole role) throws Exception {
        return asUser(request, testUsers.user(role).school(school).save());
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

    protected School saveSchool() {
        School newSchool = new School();
        newSchool.setName("Test school " + UUID.randomUUID());
        newSchool.setAddress("Test street 1");
        newSchool.setActive(true);
        return schoolRepository.saveAndFlush(newSchool);
    }

    protected User saveTeacher(School teacherSchool) {
        return testUsers.user(UserRole.TEACHER).name("Eva").surname("Ucitelova").school(teacherSchool).save();
    }

    protected SchoolClass saveClass(School classSchool, User classTeacher, String name, boolean active) {
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setSchool(classSchool);
        schoolClass.setTeacher(classTeacher);
        schoolClass.setName(name);
        schoolClass.setJoinCode(UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT));
        schoolClass.setActive(active);
        return classRepository.saveAndFlush(schoolClass);
    }

    /** A class of another teacher in the same school, with a unique name. */
    protected SchoolClass saveForeignClassInSameSchool() {
        return saveClass(school, saveTeacher(school), "Foreign " + UUID.randomUUID().toString().substring(0, 8), true);
    }

    /** A class of another teacher in another school, with a unique name. */
    protected SchoolClass saveClassInOtherSchool() {
        School otherSchool = saveSchool();
        return saveClass(otherSchool, saveTeacher(otherSchool), "Other " + UUID.randomUUID().toString().substring(0, 8), true);
    }

    protected User saveStudent(SchoolClass schoolClass) {
        return testUsers.user(UserRole.STUDENT).inClass(schoolClass).save();
    }

    protected User saveStudent(SchoolClass schoolClass, String name, String surname) {
        return testUsers.user(UserRole.STUDENT).name(name).surname(surname).inClass(schoolClass).save();
    }

    protected User saveStudent(SchoolClass schoolClass, boolean active) {
        return testUsers.user(UserRole.STUDENT).inClass(schoolClass).active(active).save();
    }

    protected CourseModule saveModule(String title, int orderNumber, boolean active) {
        CourseModule module = new CourseModule();
        module.setTitle(title);
        module.setDescription("Description of " + title);
        module.setTheoryContent("# Theory of " + title);
        module.setOrderNumber(orderNumber);
        module.setActive(active);
        return moduleRepository.saveAndFlush(module);
    }

    protected void saveProgress(User user, CourseModule module, boolean theory, boolean quiz, boolean coding) {
        UserProgress progress = new UserProgress();
        progress.setUser(user);
        progress.setModule(module);
        progress.setTheoryCompleted(theory);
        progress.setQuizCompleted(quiz);
        progress.setCodingCompleted(coding);
        progressRepository.saveAndFlush(progress);
    }

    /** Writes pending changes and forgets every loaded entity, so the next read really goes to the database. */
    protected void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    /** The saved user as the database holds it now. */
    protected User reload(User user) {
        return userRepository.findById(user.getId()).orElseThrow();
    }

    /** Numbers at the JSON path as longs (JsonPath returns small numbers as Integer). */
    protected static List<Long> longs(String body, String path) {
        List<Number> numbers = JsonPath.read(body, path);
        return numbers.stream().map(Number::longValue).toList();
    }

    protected static String progressUrl(Long studentId) {
        return URL + "/" + studentId + "/progress";
    }

    protected static String activeUrl() {
        return URL + "/active";
    }
}
