package com.tgrznar.javalearningapp.user.teacher.api;

import com.jayway.jsonpath.JsonPath;
import com.tgrznar.javalearningapp.auth.security.JwtService;
import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.school.SchoolRepository;
import com.tgrznar.javalearningapp.schoolclass.SchoolClass;
import com.tgrznar.javalearningapp.schoolclass.SchoolClassRepository;
import com.tgrznar.javalearningapp.testsupport.TestUsers;
import com.tgrznar.javalearningapp.testsupport.TestcontainersConfiguration;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

/**
 * Two teachers' requests (here: the same teacher sending the same request twice) that deactivate the same
 * students at the same moment are decided one after another: every student is reported as changed exactly once,
 * by the request that got the lock first, and as unchanged by the other one.
 * <p>
 * Deliberately NOT @Transactional (like RefreshTokenRaceIntegrationTest): each parallel request runs in its own
 * transaction and has to see committed data. So the data is committed in setUp and removed by hand in tearDown,
 * in the order of the foreign keys (students, class, teacher, school).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class TeacherStudentActiveRaceTest {

    private static final int STUDENTS = 6;
    private static final int ROUNDS = 5;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestUsers testUsers;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private SchoolClassRepository classRepository;

    @Autowired
    private JwtService jwtService;

    private Long schoolId;
    private Long teacherId;
    private Long classId;
    private String token;
    private List<Long> studentIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        School school = new School();
        school.setName("Race school " + UUID.randomUUID());
        school.setAddress("Test street 1");
        school.setActive(true);
        school = schoolRepository.saveAndFlush(school);
        schoolId = school.getId();

        User teacher = testUsers.user(UserRole.TEACHER).school(school).save();
        teacherId = teacher.getId();
        token = jwtService.generateAccessToken(teacher);

        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setSchool(school);
        schoolClass.setTeacher(teacher);
        schoolClass.setName("Race class");
        schoolClass.setJoinCode(UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT));
        schoolClass.setActive(true);
        schoolClass = classRepository.saveAndFlush(schoolClass);
        classId = schoolClass.getId();

        for (int i = 0; i < STUDENTS; i++) {
            studentIds.add(testUsers.user(UserRole.STUDENT).inClass(schoolClass).save().getId());
        }
    }

    @AfterEach
    void tearDown() {
        // refresh tokens of the students go with them (ON DELETE CASCADE)
        if (classId != null) {
            jdbcTemplate.update("DELETE FROM users WHERE class_id = ?", classId);
            jdbcTemplate.update("DELETE FROM classes WHERE id = ?", classId);
        }
        if (teacherId != null) {
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", teacherId);
        }
        if (schoolId != null) {
            jdbcTemplate.update("DELETE FROM schools WHERE id = ?", schoolId);
        }
    }

    @Test
    void sameStudentsDeactivatedAtTheSameTime_areChangedExactlyOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 1; round <= ROUNDS; round++) {
                jdbcTemplate.update("UPDATE users SET is_active = true WHERE class_id = ?", classId);

                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch go = new CountDownLatch(1);
                List<Future<MockHttpServletResponse>> responses = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    responses.add(pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        return deactivateAll();
                    }));
                }
                ready.await();
                go.countDown();

                List<Long> changed = new ArrayList<>();
                List<Long> unchanged = new ArrayList<>();
                for (Future<MockHttpServletResponse> response : responses) {
                    MockHttpServletResponse result = response.get(30, TimeUnit.SECONDS);
                    assertThat(result.getStatus()).as("round %d", round).isEqualTo(200);
                    changed.addAll(numbers(result.getContentAsString(), "$.changed"));
                    unchanged.addAll(numbers(result.getContentAsString(), "$.unchanged"));
                }

                // Every student is changed once and reported as unchanged once, never changed twice.
                assertThat(changed).as("changed in round %d", round).containsExactlyInAnyOrderElementsOf(studentIds);
                assertThat(unchanged).as("unchanged in round %d", round).containsExactlyInAnyOrderElementsOf(studentIds);
                assertThat(activeStudents()).as("active students after round %d", round).isZero();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private MockHttpServletResponse deactivateAll() throws Exception {
        String ids = studentIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        return mockMvc.perform(patch("/api/v1/teacher/students/active")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentIds\":[" + ids + "],\"active\":false}"))
                .andReturn().getResponse();
    }

    private static List<Long> numbers(String body, String path) {
        List<Number> numbers = JsonPath.read(body, path);
        return numbers.stream().map(Number::longValue).toList();
    }

    private int activeStudents() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE class_id = ? AND is_active = true", Integer.class, classId);
        return count == null ? 0 : count;
    }
}
