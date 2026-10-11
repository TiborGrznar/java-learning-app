package com.tgrznar.javalearningapp.quizquestion.admin.api;

import com.tgrznar.javalearningapp.auth.security.JwtService;
import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.module.CourseModuleRepository;
import com.tgrznar.javalearningapp.quizquestion.QuizOption;
import com.tgrznar.javalearningapp.quizquestion.QuizQuestion;
import com.tgrznar.javalearningapp.quizquestion.QuizQuestionRepository;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.junit.jupiter.api.Test;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests of the administrator's quiz question management
 * (/api/v1/modules/{moduleId}/questions). Test data uses unique e-mails and is rolled back
 * after each test, order numbers of directly saved modules are high so they do not collide
 * with real content.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminQuestionApiIntegrationTest {

    private static final long UNKNOWN_ID = 999_999_999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseModuleRepository moduleRepository;

    @Autowired
    private QuizQuestionRepository questionRepository;

    @Autowired
    private JwtService jwtService;

    // ---------- access control ----------

    @Test
    void list_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(base(1L)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void list_asStudent_returns403() throws Exception {
        CourseModule module = saveModule("Module", true);

        as(get(base(module.getId())), UserRole.STUDENT)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void list_asTeacher_returns403() throws Exception {
        CourseModule module = saveModule("Module", true);

        as(get(base(module.getId())), UserRole.TEACHER)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void get_asStudent_returns403() throws Exception {
        CourseModule module = saveModule("Module", true);
        QuizQuestion question = saveQuestion(module, "Question");

        as(get(url(module.getId(), question.getId())), UserRole.STUDENT)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void create_asTeacher_returns403AndCreatesNothing() throws Exception {
        CourseModule module = saveModule("Module", true);
        long before = questionRepository.count();

        as(post(base(module.getId())).contentType(MediaType.APPLICATION_JSON).content(validJson()), UserRole.TEACHER)
                .andExpect(status().isForbidden());

        assertThat(questionRepository.count()).isEqualTo(before);
    }

    @Test
    void update_asStudent_returns403AndChangesNothing() throws Exception {
        CourseModule module = saveModule("Module", true);
        QuizQuestion question = saveQuestion(module, "Original");

        as(put(url(module.getId(), question.getId())).contentType(MediaType.APPLICATION_JSON).content(validJson()),
                UserRole.STUDENT)
                .andExpect(status().isForbidden());

        assertThat(questionRepository.findById(question.getId()).orElseThrow().getQuestionText())
                .isEqualTo("Original");
    }

    @Test
    void delete_asTeacher_returns403AndDeletesNothing() throws Exception {
        CourseModule module = saveModule("Module", true);
        QuizQuestion question = saveQuestion(module, "Question");

        as(delete(url(module.getId(), question.getId())), UserRole.TEACHER)
                .andExpect(status().isForbidden());

        assertThat(questionRepository.findById(question.getId())).isPresent();
    }

    // ---------- create ----------

    @Test
    void create_returns201WithLocationAndFullBody() throws Exception {
        CourseModule module = saveModule("Module", true);

        as(post(base(module.getId())).contentType(MediaType.APPLICATION_JSON)
                        .content(json("Co je JVM?", "Virtualny stroj", "Editor", "Kompilator", "Operacny system", "A")),
                UserRole.ADMIN)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(base(module.getId()) + "/")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.moduleId").value(module.getId()))
                .andExpect(jsonPath("$.questionText").value("Co je JVM?"))
                .andExpect(jsonPath("$.optionA").value("Virtualny stroj"))
                .andExpect(jsonPath("$.optionD").value("Operacny system"))
                .andExpect(jsonPath("$.correctOption").value("A"));
    }

    @Test
    void create_trimsText() throws Exception {
        CourseModule module = saveModule("Module", true);

        as(post(base(module.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(json("  Question  ", " A ", " B ", " C ", " D ", "B")), UserRole.ADMIN)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.questionText").value("Question"))
                .andExpect(jsonPath("$.optionA").value("A"))
                .andExpect(jsonPath("$.optionC").value("C"));
    }

    @Test
    void create_inInactiveModule_returns201() throws Exception {
        CourseModule module = saveModule("Hidden module", false);

        as(post(base(module.getId())).contentType(MediaType.APPLICATION_JSON).content(validJson()), UserRole.ADMIN)
                .andExpect(status().isCreated());
    }

    @Test
    void create_unknownModule_returns404() throws Exception {
        as(post(base(UNKNOWN_ID)).contentType(MediaType.APPLICATION_JSON).content(validJson()), UserRole.ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_FOUND"));
    }

    @Test
    void create_blankQuestionText_returns400WithFieldError() throws Exception {
        CourseModule module = saveModule("Module", true);

        as(post(base(module.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(json("   ", "A", "B", "C", "D", "A")), UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.questionText").exists());
    }

    @Test
    void create_blankOption_returns400WithFieldError() throws Exception {
        CourseModule module = saveModule("Module", true);

        as(post(base(module.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(json("Question", "A", "B", "   ", "D", "A")), UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.optionC").exists());
    }

    @Test
    void create_questionTextTooLong_returns400WithFieldError() throws Exception {
        CourseModule module = saveModule("Module", true);

        as(post(base(module.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(json("a".repeat(1_001), "A", "B", "C", "D", "A")), UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.questionText").exists());
    }

    @Test
    void create_optionTooLong_returns400WithFieldError() throws Exception {
        CourseModule module = saveModule("Module", true);

        as(post(base(module.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(json("Question", "A", "a".repeat(501), "C", "D", "A")), UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.optionB").exists());
    }

    @Test
    void create_missingCorrectOption_returns400WithFieldError() throws Exception {
        CourseModule module = saveModule("Module", true);

        as(post(base(module.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(json("Question", "A", "B", "C", "D", null)), UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.correctOption").exists());
    }

    @Test
    void create_invalidCorrectOption_returns400InvalidRequestBody() throws Exception {
        CourseModule module = saveModule("Module", true);

        as(post(base(module.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(json("Question", "A", "B", "C", "D", "E")), UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
    }

    // ---------- read ----------

    @Test
    void list_returnsOnlyTheModulesQuestionsInCreationOrder() throws Exception {
        CourseModule module = saveModule("Module", true);
        CourseModule other = saveModule("Other module", true);
        QuizQuestion first = saveQuestion(module, "First");
        QuizQuestion second = saveQuestion(module, "Second");
        saveQuestion(other, "Foreign");

        as(get(base(module.getId())), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(first.getId()))
                .andExpect(jsonPath("$[1].id").value(second.getId()))
                .andExpect(jsonPath("$[0].correctOption").value("B"));
    }

    @Test
    void list_unknownModule_returns404() throws Exception {
        as(get(base(UNKNOWN_ID)), UserRole.ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_FOUND"));
    }

    @Test
    void get_returnsQuestionWithCorrectOption() throws Exception {
        CourseModule module = saveModule("Module", true);
        QuizQuestion question = saveQuestion(module, "Detail");

        as(get(url(module.getId(), question.getId())), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(question.getId()))
                .andExpect(jsonPath("$.questionText").value("Detail"))
                .andExpect(jsonPath("$.correctOption").value("B"));
    }

    @Test
    void get_unknownQuestion_returns404() throws Exception {
        CourseModule module = saveModule("Module", true);

        as(get(url(module.getId(), UNKNOWN_ID)), UserRole.ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUESTION_NOT_FOUND"));
    }

    @Test
    void get_questionOfAnotherModule_returns404() throws Exception {
        CourseModule module = saveModule("Module", true);
        CourseModule other = saveModule("Other module", true);
        QuizQuestion question = saveQuestion(other, "Foreign");

        as(get(url(module.getId(), question.getId())), UserRole.ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUESTION_NOT_FOUND"));
    }

    @Test
    void get_unknownModule_returns404() throws Exception {
        as(get(url(UNKNOWN_ID, 1L)), UserRole.ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_FOUND"));
    }

    // ---------- update ----------

    @Test
    void update_replacesAllFields() throws Exception {
        CourseModule module = saveModule("Module", true);
        QuizQuestion question = saveQuestion(module, "Original");

        as(put(url(module.getId(), question.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(json(" New text ", "New A", "New B", "New C", "New D", "D")), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionText").value("New text"))
                .andExpect(jsonPath("$.optionA").value("New A"))
                .andExpect(jsonPath("$.optionD").value("New D"))
                .andExpect(jsonPath("$.correctOption").value("D"));

        assertThat(questionRepository.findById(question.getId()).orElseThrow().getCorrectOption())
                .isEqualTo(QuizOption.D);
    }

    @Test
    void update_questionOfAnotherModule_returns404AndChangesNothing() throws Exception {
        CourseModule module = saveModule("Module", true);
        CourseModule other = saveModule("Other module", true);
        QuizQuestion question = saveQuestion(other, "Foreign");

        as(put(url(module.getId(), question.getId())).contentType(MediaType.APPLICATION_JSON).content(validJson()),
                UserRole.ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUESTION_NOT_FOUND"));

        assertThat(questionRepository.findById(question.getId()).orElseThrow().getQuestionText())
                .isEqualTo("Foreign");
    }

    @Test
    void update_unknownQuestion_returns404() throws Exception {
        CourseModule module = saveModule("Module", true);

        as(put(url(module.getId(), UNKNOWN_ID)).contentType(MediaType.APPLICATION_JSON).content(validJson()),
                UserRole.ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUESTION_NOT_FOUND"));
    }

    @Test
    void update_blankQuestionText_returns400WithFieldError() throws Exception {
        CourseModule module = saveModule("Module", true);
        QuizQuestion question = saveQuestion(module, "Original");

        as(put(url(module.getId(), question.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(json("", "A", "B", "C", "D", "A")), UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.questionText").exists());
    }

    // ---------- delete ----------

    @Test
    void delete_removesTheQuestion() throws Exception {
        CourseModule module = saveModule("Module", true);
        QuizQuestion question = saveQuestion(module, "Question");

        as(delete(url(module.getId(), question.getId())), UserRole.ADMIN)
                .andExpect(status().isNoContent());

        assertThat(questionRepository.findById(question.getId())).isEmpty();
    }

    @Test
    void delete_questionOfAnotherModule_returns404AndKeepsIt() throws Exception {
        CourseModule module = saveModule("Module", true);
        CourseModule other = saveModule("Other module", true);
        QuizQuestion question = saveQuestion(other, "Foreign");

        as(delete(url(module.getId(), question.getId())), UserRole.ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUESTION_NOT_FOUND"));

        assertThat(questionRepository.findById(question.getId())).isPresent();
    }

    @Test
    void delete_unknownQuestion_returns404() throws Exception {
        CourseModule module = saveModule("Module", true);

        as(delete(url(module.getId(), UNKNOWN_ID)), UserRole.ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUESTION_NOT_FOUND"));
    }

    // ---------- helpers ----------

    private ResultActions as(MockHttpServletRequestBuilder request, UserRole role) throws Exception {
        String token = jwtService.generateAccessToken(saveUser(role));
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
        return userRepository.saveAndFlush(user);
    }

    private CourseModule saveModule(String title, boolean active) {
        CourseModule module = new CourseModule();
        module.setTitle(title);
        module.setDescription("Description of " + title);
        module.setTheoryContent("# Theory of " + title);
        module.setOrderNumber(9_001);
        module.setActive(active);
        return moduleRepository.saveAndFlush(module);
    }

    private QuizQuestion saveQuestion(CourseModule module, String text) {
        QuizQuestion question = new QuizQuestion();
        question.setModule(module);
        question.setQuestionText(text);
        question.setOptionA("Option A");
        question.setOptionB("Option B");
        question.setOptionC("Option C");
        question.setOptionD("Option D");
        question.setCorrectOption(QuizOption.B);
        return questionRepository.saveAndFlush(question);
    }

    private static String base(Long moduleId) {
        return "/api/v1/modules/" + moduleId + "/questions";
    }

    private static String url(Long moduleId, Long id) {
        return base(moduleId) + "/" + id;
    }

    private static String validJson() {
        return json("Question", "A", "B", "C", "D", "A");
    }

    private static String json(String questionText, String a, String b, String c, String d, String correct) {
        return "{\"questionText\":" + quote(questionText) + ",\"optionA\":" + quote(a)
                + ",\"optionB\":" + quote(b) + ",\"optionC\":" + quote(c) + ",\"optionD\":" + quote(d)
                + ",\"correctOption\":" + quote(correct) + "}";
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}