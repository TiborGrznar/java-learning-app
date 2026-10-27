package com.tgrznar.javalearningapp.learning.quiz.api;

import com.jayway.jsonpath.JsonPath;
import com.tgrznar.javalearningapp.auth.security.JwtService;
import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.module.CourseModuleRepository;
import com.tgrznar.javalearningapp.quizquestion.QuizOption;
import com.tgrznar.javalearningapp.quizquestion.QuizQuestion;
import com.tgrznar.javalearningapp.quizquestion.QuizQuestionRepository;
import com.tgrznar.javalearningapp.quizresult.QuizResultRepository;
import com.tgrznar.javalearningapp.school.SchoolRepository;
import com.tgrznar.javalearningapp.schoolclass.SchoolClass;
import com.tgrznar.javalearningapp.schoolclass.SchoolClassRepository;
import com.tgrznar.javalearningapp.testsupport.EnrolledClassFactory;
import com.tgrznar.javalearningapp.testsupport.TestUsers;
import com.tgrznar.javalearningapp.testsupport.TestcontainersConfiguration;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.user.model.UserRole;
import com.tgrznar.javalearningapp.userprogress.UserProgress;
import com.tgrznar.javalearningapp.userprogress.UserProgressRepository;
import org.junit.jupiter.api.AfterEach;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A quiz token works for one submission only, also when two requests with the same token arrive at the
 * same moment. The unique key on quiz_results.quiz_token_id decides which one wins.
 * <p>
 * Deliberately NOT @Transactional (unlike the other integration tests): requests running in parallel
 * threads each have their own transaction and must see committed data. So the test data is committed
 * and removed by hand in tearDown, children before parents because of the RESTRICT foreign keys.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class StudentQuizTokenReuseIntegrationTest {

    private static final String MODULES_URL = "/api/v1/learning/modules";
    private static final int ROUNDS = 5;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TestUsers testUsers;

    @Autowired
    private EnrolledClassFactory enrolledClasses;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private SchoolClassRepository classRepository;

    @Autowired
    private CourseModuleRepository moduleRepository;

    @Autowired
    private QuizQuestionRepository questionRepository;

    @Autowired
    private QuizResultRepository resultRepository;

    @Autowired
    private UserProgressRepository progressRepository;

    private Long schoolId;
    private Long teacherId;
    private Long classId;
    private Long moduleId;
    private User student;
    private List<QuizQuestion> questions;

    @BeforeEach
    void setUp() {
        SchoolClass schoolClass = enrolledClasses.saveClass(true);
        classId = schoolClass.getId();
        schoolId = schoolClass.getSchool().getId();
        teacherId = schoolClass.getTeacher().getId();
        student = testUsers.user(UserRole.STUDENT).inClass(schoolClass).save();

        CourseModule module = new CourseModule();
        module.setTitle("Token reuse test module");
        module.setDescription("Description");
        module.setTheoryContent("# Theory");
        module.setOrderNumber(1);
        module.setActive(true);
        module = moduleRepository.saveAndFlush(module);
        moduleId = module.getId();

        questions = new ArrayList<>();
        for (QuizOption correct : List.of(QuizOption.A, QuizOption.B, QuizOption.C)) {
            QuizQuestion question = new QuizQuestion();
            question.setModule(module);
            question.setQuestionText("Question");
            question.setOptionA("Option A");
            question.setOptionB("Option B");
            question.setOptionC("Option C");
            question.setOptionD("Option D");
            question.setCorrectOption(correct);
            questions.add(questionRepository.saveAndFlush(question));
        }

        UserProgress progress = new UserProgress();
        progress.setUser(student);
        progress.setModule(module);
        progress.setTheoryCompleted(true);
        progressRepository.saveAndFlush(progress);
    }

    @AfterEach
    void tearDown() {
        // Deleting the student removes the quiz results and the progress too (ON DELETE CASCADE, V5).
        userRepository.deleteById(student.getId());
        questionRepository.deleteAllById(questions.stream().map(QuizQuestion::getId).toList());
        moduleRepository.deleteById(moduleId);
        classRepository.deleteById(classId);
        userRepository.deleteById(teacherId);
        schoolRepository.deleteById(schoolId);
    }

    @Test
    void sameTokenSentTwice_secondSubmissionIsRejectedAndOnlyOneResultIsStored() throws Exception {
        String body = correctSubmission(loadToken());

        submit(body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true));
        submit(body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUIZ_ALREADY_SUBMITTED"));

        assertThat(resultRepository.findByUserId(student.getId())).hasSize(1);
    }

    @Test
    void invalidAnswers_doNotUseUpTheToken() throws Exception {
        String token = loadToken();

        // Only one of three answers: rejected as a validation error, nothing is stored.
        submit("{\"quizToken\":\"" + token + "\",\"answers\":[{\"questionId\":" + questions.get(0).getId()
                + ",\"option\":\"A\"}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(resultRepository.findByUserId(student.getId())).isEmpty();

        // The same token still works, because the rejected request did not use it up.
        submit(correctSubmission(token)).andExpect(status().isOk());
        assertThat(resultRepository.findByUserId(student.getId())).hasSize(1);
    }

    @Test
    void everyLoadedQuizIsSubmittableOnce() throws Exception {
        submit(correctSubmission(loadToken())).andExpect(status().isOk());
        submit(correctSubmission(loadToken())).andExpect(status().isOk());

        assertThat(resultRepository.findByUserId(student.getId())).hasSize(2);
    }

    @Test
    void sameTokenSentAtTheSameTime_succeedsExactlyOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 1; round <= ROUNDS; round++) {
                String body = correctSubmission(loadToken());
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch go = new CountDownLatch(1);

                List<Future<Integer>> responses = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    responses.add(pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        return submit(body).andReturn().getResponse().getStatus();
                    }));
                }
                ready.await();
                go.countDown();

                List<Integer> statuses = new ArrayList<>();
                for (Future<Integer> response : responses) {
                    statuses.add(response.get(30, TimeUnit.SECONDS));
                }
                assertThat(statuses).as("round %d", round).containsExactlyInAnyOrder(200, 409);
            }
        } finally {
            pool.shutdownNow();
        }

        // One stored result per round, never two for the same token.
        assertThat(resultRepository.findByUserId(student.getId())).hasSize(ROUNDS);
    }

    // ---------- helpers ----------

    private ResultActions submit(String body) throws Exception {
        return mockMvc.perform(post(MODULES_URL + "/" + moduleId + "/quiz/submissions")
                .header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String loadToken() throws Exception {
        MvcResult result = mockMvc.perform(get(MODULES_URL + "/" + moduleId + "/quiz")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.quizToken");
    }

    private String bearer() {
        return "Bearer " + jwtService.generateAccessToken(student);
    }

    /** All three questions are drawn (the bank is smaller than the quiz size), every answer is correct. */
    private String correctSubmission(String token) {
        String answers = questions.stream()
                .map(question -> "{\"questionId\":" + question.getId()
                        + ",\"option\":\"" + question.getCorrectOption().name() + "\"}")
                .collect(Collectors.joining(","));
        return "{\"quizToken\":\"" + token + "\",\"answers\":[" + answers + "]}";
    }
}