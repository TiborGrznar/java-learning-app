package com.tgrznar.javalearningapp.learning.quiz.api;

import com.jayway.jsonpath.JsonPath;
import com.tgrznar.javalearningapp.auth.security.JwtService;
import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.module.CourseModuleRepository;
import com.tgrznar.javalearningapp.quizquestion.QuizOption;
import com.tgrznar.javalearningapp.quizquestion.QuizQuestion;
import com.tgrznar.javalearningapp.quizquestion.QuizQuestionRepository;
import com.tgrznar.javalearningapp.quizresult.QuizResult;
import com.tgrznar.javalearningapp.quizresult.QuizResultRepository;
import com.tgrznar.javalearningapp.testsupport.EnrolledClassFactory;
import com.tgrznar.javalearningapp.testsupport.TestUsers;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRole;
import com.tgrznar.javalearningapp.userprogress.UserProgress;
import com.tgrznar.javalearningapp.userprogress.UserProgressRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import com.tgrznar.javalearningapp.testsupport.TestcontainersConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/**
 * Integration tests of loading and submitting a module quiz
 * (/api/v1/learning/modules/{id}/quiz). A bank of at most ten questions is drawn in full, so the
 * tests know which questions to answer. Test data uses unique e-mails and is rolled back after each test.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
class StudentQuizApiIntegrationTest {

    private static final String MODULES_URL = "/api/v1/learning/modules";
    private static final long UNKNOWN_ID = 999_999_999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CourseModuleRepository moduleRepository;

    @Autowired
    private QuizQuestionRepository questionRepository;

    @Autowired
    private QuizResultRepository resultRepository;

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
    void load_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(quizUrl(1L)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void load_asAdmin_returns403() throws Exception {
        CourseModule module = saveModule("First", 1, true);

        as(get(quizUrl(module.getId())), UserRole.ADMIN)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void load_asTeacher_returns403() throws Exception {
        CourseModule module = saveModule("First", 1, true);

        as(get(quizUrl(module.getId())), UserRole.TEACHER)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void submit_asAdmin_returns403() throws Exception {
        CourseModule module = saveModule("First", 1, true);

        as(post(submitUrl(module.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(submission("token", "1:A")), UserRole.ADMIN)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    // ---------- loading ----------

    @Test
    void load_returnsQuestionsWithFourOptionsAndNoCorrectAnswer() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        saveQuestions(module, QuizOption.A, QuizOption.B, QuizOption.C);
        User student = studentWithTheory(module);

        asUser(get(quizUrl(module.getId())), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quizToken").isNotEmpty())
                .andExpect(jsonPath("$.questions.length()").value(3))
                .andExpect(jsonPath("$.questions[0].text").exists())
                .andExpect(jsonPath("$.questions[0].options.length()").value(4))
                .andExpect(jsonPath("$.questions[0].options[0].key").exists())
                .andExpect(jsonPath("$.questions[0].options[0].text").exists())
                .andExpect(jsonPath("$.questions[0].correctOption").doesNotExist());
    }

    @Test
    void load_drawsAtMostTenQuestionsFromALargerBank() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        saveBank(module, 12);
        User student = studentWithTheory(module);

        asUser(get(quizUrl(module.getId())), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions.length()").value(10));
    }

    @Test
    void load_theoryNotCompleted_returns409() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        saveQuestions(module, QuizOption.A);

        as(get(quizUrl(module.getId())), UserRole.STUDENT)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("THEORY_NOT_COMPLETED"));
    }

    @Test
    void load_lockedModule_returns403() throws Exception {
        saveModule("First", 1, true);
        CourseModule second = saveModule("Second", 2, true);
        saveQuestions(second, QuizOption.A);

        as(get(quizUrl(second.getId())), UserRole.STUDENT)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_LOCKED"));
    }

    @Test
    void load_moduleWithoutQuestions_returns409() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        User student = studentWithTheory(module);

        asUser(get(quizUrl(module.getId())), student)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUIZ_NOT_AVAILABLE"));
    }

    @Test
    void load_inactiveModule_returns404() throws Exception {
        CourseModule module = saveModule("Hidden", 1, false);

        as(get(quizUrl(module.getId())), UserRole.STUDENT)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_FOUND"));
    }

    @Test
    void load_unknownModule_returns404() throws Exception {
        as(get(quizUrl(UNKNOWN_ID)), UserRole.STUDENT)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_FOUND"));
    }

    // ---------- submitting ----------

    @Test
    void submit_allCorrect_passesAndStoresTheResult() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        List<QuizQuestion> questions = saveQuestions(module, QuizOption.A, QuizOption.B, QuizOption.C);
        User student = studentWithTheory(module);
        String token = loadToken(student, module.getId());

        submit(student, module.getId(), submission(token, correctAnswers(questions)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(3))
                .andExpect(jsonPath("$.maxScore").value(3))
                .andExpect(jsonPath("$.percent").value(100))
                .andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.answers.length()").value(3))
                .andExpect(jsonPath("$.answers[0].correct").value(true))
                .andExpect(jsonPath("$.answers[0].correct").value(true))
                .andExpect(jsonPath("$.answers[0].correctOption").exists());

        List<QuizResult> results = resultRepository.findByUserId(student.getId());
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getScore()).isEqualTo(3);
        assertThat(results.get(0).getMaxScore()).isEqualTo(3);
        UserProgress progress = progressRepository.findByUserIdAndModuleId(student.getId(), module.getId())
                .orElseThrow();
        assertThat(progress.isQuizCompleted()).isTrue();
        assertThat(progress.isTheoryCompleted()).isTrue();
    }

    @Test
    void submit_belowThreshold_storesTheResultWithoutPassing() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        List<QuizQuestion> questions = saveQuestions(module, QuizOption.A, QuizOption.B, QuizOption.C);
        User student = studentWithTheory(module);
        String token = loadToken(student, module.getId());
        String[] correct = correctAnswers(questions);

        submit(student, module.getId(), submission(token,
                correct[0], wrongAnswer(questions.get(1)), wrongAnswer(questions.get(2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(1))
                .andExpect(jsonPath("$.percent").value(33))
                .andExpect(jsonPath("$.passed").value(false));

        assertThat(resultRepository.findByUserId(student.getId())).hasSize(1);
        UserProgress progress = progressRepository.findByUserIdAndModuleId(student.getId(), module.getId())
                .orElseThrow();
        assertThat(progress.isQuizCompleted()).isFalse();
    }

    @Test
    void submit_failedAttempt_hidesTheCorrectOptionsButMarksTheAnswers() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        List<QuizQuestion> questions = saveQuestions(module, QuizOption.A, QuizOption.B, QuizOption.C);
        User student = studentWithTheory(module);
        String token = loadToken(student, module.getId());
        String[] correct = correctAnswers(questions);

        // The answers come back in the shuffled order of the drawn quiz, so the checks do not depend on it.
        submit(student, module.getId(), submission(token,
                correct[0], wrongAnswer(questions.get(1)), wrongAnswer(questions.get(2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(false))
                .andExpect(jsonPath("$.answers.length()").value(3))
                .andExpect(jsonPath("$.answers[?(@.correct == true)]").value(hasSize(1)))
                .andExpect(jsonPath("$.answers[?(@.correct == false)]").value(hasSize(2)))
                .andExpect(jsonPath("$.answers[*].selected").value(hasSize(3)))
                .andExpect(content().string(not(containsString("correctOption"))));
    }
    @Test
    void submit_failedRetry_keepsAnEarlierPass() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        List<QuizQuestion> questions = saveQuestions(module, QuizOption.A, QuizOption.B, QuizOption.C);
        User student = saveUser(UserRole.STUDENT);
        saveProgress(student, module, true, true, false);
        String token = loadToken(student, module.getId());

        submit(student, module.getId(), submission(token,
                wrongAnswer(questions.get(0)), wrongAnswer(questions.get(1)), wrongAnswer(questions.get(2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(false));

        UserProgress progress = progressRepository.findByUserIdAndModuleId(student.getId(), module.getId())
                .orElseThrow();
        assertThat(progress.isQuizCompleted()).isTrue();
    }

    @Test
    void submit_passing_unlocksTheNextModule() throws Exception {
        CourseModule first = saveModule("First", 1, true);
        saveModule("Second", 2, true);
        List<QuizQuestion> questions = saveQuestions(first, QuizOption.A, QuizOption.B);
        User student = studentWithTheory(first);
        String token = loadToken(student, first.getId());

        submit(student, first.getId(), submission(token, correctAnswers(questions)))
                .andExpect(status().isOk());

        asUser(get(MODULES_URL), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].locked").value(false));
    }

    @Test
    void submit_everyAttemptIsStored() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        List<QuizQuestion> questions = saveQuestions(module, QuizOption.A, QuizOption.B);
        User student = studentWithTheory(module);

        // A token works once, so each attempt starts by loading a new quiz.
        submit(student, module.getId(), submission(loadToken(student, module.getId()), correctAnswers(questions)))
                .andExpect(status().isOk());
        submit(student, module.getId(), submission(loadToken(student, module.getId()), correctAnswers(questions)))
                .andExpect(status().isOk());

        assertThat(resultRepository.findByUserId(student.getId())).hasSize(2);
    }

    // ---------- invalid submissions ----------

    @Test
    void submit_missingToken_returns400WithFieldError() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        User student = studentWithTheory(module);

        submit(student, module.getId(), submission(null, "1:A"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.quizToken").exists());
    }

    @Test
    void submit_emptyAnswers_returns400WithFieldError() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        User student = studentWithTheory(module);

        submit(student, module.getId(), submission("token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.answers").exists());
    }

    @Test
    void submit_garbageToken_returns400() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        saveQuestions(module, QuizOption.A);
        User student = studentWithTheory(module);

        submit(student, module.getId(), submission("not-a-token", "1:A"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUIZ_TOKEN"));
    }

    @Test
    void submit_accessTokenAsQuizToken_returns400() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        saveQuestions(module, QuizOption.A);
        User student = studentWithTheory(module);

        submit(student, module.getId(), submission(jwtService.generateAccessToken(student), "1:A"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUIZ_TOKEN"));
    }

    @Test
    void submit_tokenOfAnotherStudent_returns400() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        List<QuizQuestion> questions = saveQuestions(module, QuizOption.A, QuizOption.B);
        User student = studentWithTheory(module);
        User other = studentWithTheory(module);
        String token = loadToken(student, module.getId());

        submit(other, module.getId(), submission(token, correctAnswers(questions)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUIZ_TOKEN"));

        assertThat(resultRepository.findByUserId(other.getId())).isEmpty();
    }

    @Test
    void submit_tokenOfAnotherModule_returns400() throws Exception {
        CourseModule first = saveModule("First", 1, true);
        CourseModule second = saveModule("Second", 2, true);
        saveQuestions(first, QuizOption.A);
        saveQuestions(second, QuizOption.A);
        User student = studentWithTheory(first);
        saveProgress(student, second, true, false, false);
        String token = loadToken(student, first.getId());

        submit(student, second.getId(), submission(token, "1:A"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUIZ_TOKEN"));
    }

    @Test
    void submit_missingAnswer_returns400WithFieldError() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        List<QuizQuestion> questions = saveQuestions(module, QuizOption.A, QuizOption.B, QuizOption.C);
        User student = studentWithTheory(module);
        String token = loadToken(student, module.getId());
        String[] correct = correctAnswers(questions);

        submit(student, module.getId(), submission(token, correct[0], correct[1]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.answers").exists());

        assertThat(resultRepository.findByUserId(student.getId())).isEmpty();
    }

    @Test
    void submit_duplicateAnswer_returns400WithFieldError() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        List<QuizQuestion> questions = saveQuestions(module, QuizOption.A, QuizOption.B, QuizOption.C);
        User student = studentWithTheory(module);
        String token = loadToken(student, module.getId());
        String[] correct = correctAnswers(questions);

        submit(student, module.getId(), submission(token, correct[0], correct[0], correct[1], correct[2]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.answers").exists());
    }

    @Test
    void submit_invalidOption_returns400InvalidRequestBody() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        List<QuizQuestion> questions = saveQuestions(module, QuizOption.A);
        User student = studentWithTheory(module);
        String token = loadToken(student, module.getId());

        submit(student, module.getId(), submission(token, questions.get(0).getId() + ":E"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
    }

    @Test
    void submit_questionDeletedAfterLoading_returns409() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        List<QuizQuestion> questions = saveQuestions(module, QuizOption.A, QuizOption.B, QuizOption.C);
        User student = studentWithTheory(module);
        String token = loadToken(student, module.getId());
        questionRepository.delete(questions.get(2));
        questionRepository.flush();

        submit(student, module.getId(), submission(token, correctAnswers(questions)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUIZ_OUTDATED"));

        assertThat(resultRepository.findByUserId(student.getId())).isEmpty();
    }

    @Test
    void submit_theoryNotCompleted_returns409() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        saveQuestions(module, QuizOption.A);
        User student = saveUser(UserRole.STUDENT);

        submit(student, module.getId(), submission("token", "1:A"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("THEORY_NOT_COMPLETED"));
    }

    @Test
    void submit_lockedModule_returns403() throws Exception {
        saveModule("First", 1, true);
        CourseModule second = saveModule("Second", 2, true);
        saveQuestions(second, QuizOption.A);
        User student = saveUser(UserRole.STUDENT);

        submit(student, second.getId(), submission("token", "1:A"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_LOCKED"));
    }

    // ---------- module detail ----------

    @Test
    void moduleDetail_capsTheQuestionCountAtTheQuizSize() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        saveBank(module, 12);
        User student = saveUser(UserRole.STUDENT);

        asUser(get(MODULES_URL + "/" + module.getId()), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quizQuestionCount").value(10));
    }

    // ---------- helpers ----------

    private ResultActions as(MockHttpServletRequestBuilder request, UserRole role) throws Exception {
        return asUser(request, saveUser(role));
    }

    private ResultActions asUser(MockHttpServletRequestBuilder request, User user) throws Exception {
        String token = jwtService.generateAccessToken(user);
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions submit(User student, Long moduleId, String body) throws Exception {
        return asUser(post(submitUrl(moduleId)).contentType(MediaType.APPLICATION_JSON).content(body), student);
    }

    private String loadToken(User student, Long moduleId) throws Exception {
        MvcResult result = asUser(get(quizUrl(moduleId)), student)
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.quizToken");
    }

    private User saveUser(UserRole role) {
        // Learning endpoints are open only to students who joined a class.
        return role == UserRole.STUDENT
                ? enrolledClasses.saveEnrolledStudent()
                : testUsers.user(role).save();
    }

    private User studentWithTheory(CourseModule module) {
        User student = saveUser(UserRole.STUDENT);
        saveProgress(student, module, true, false, false);
        return student;
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

    private List<QuizQuestion> saveQuestions(CourseModule module, QuizOption... correctOptions) {
        List<QuizQuestion> saved = new ArrayList<>();
        for (QuizOption correct : correctOptions) {
            saved.add(saveQuestion(module, correct));
        }
        return saved;
    }

    private void saveBank(CourseModule module, int size) {
        for (int i = 0; i < size; i++) {
            saveQuestion(module, QuizOption.A);
        }
    }

    private QuizQuestion saveQuestion(CourseModule module, QuizOption correct) {
        QuizQuestion question = new QuizQuestion();
        question.setModule(module);
        question.setQuestionText("Question");
        question.setOptionA("Option A");
        question.setOptionB("Option B");
        question.setOptionC("Option C");
        question.setOptionD("Option D");
        question.setCorrectOption(correct);
        return questionRepository.saveAndFlush(question);
    }

    private static String quizUrl(Long moduleId) {
        return MODULES_URL + "/" + moduleId + "/quiz";
    }

    private static String submitUrl(Long moduleId) {
        return quizUrl(moduleId) + "/submissions";
    }

    private static String[] correctAnswers(List<QuizQuestion> questions) {
        return questions.stream()
                .map(question -> question.getId() + ":" + question.getCorrectOption().name())
                .toArray(String[]::new);
    }

    private static String wrongAnswer(QuizQuestion question) {
        return question.getId() + ":" + (question.getCorrectOption() == QuizOption.A ? "B" : "A");
    }

    /** Each answer is written as "questionId:OPTION", for example "12:C". */
    private static String submission(String token, String... answers) {
        String items = Arrays.stream(answers)
                .map(answer -> {
                    String[] parts = answer.split(":");
                    return "{\"questionId\":" + parts[0] + ",\"option\":\"" + parts[1] + "\"}";
                })
                .collect(Collectors.joining(","));
        return "{\"quizToken\":" + quote(token) + ",\"answers\":[" + items + "]}";
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}