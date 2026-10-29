package com.tgrznar.javalearningapp.learning.quiz.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tgrznar.javalearningapp.auth.security.JwtService;
import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.module.CourseModuleRepository;
import com.tgrznar.javalearningapp.quizquestion.QuizOption;
import com.tgrznar.javalearningapp.quizquestion.QuizQuestion;
import com.tgrznar.javalearningapp.quizquestion.QuizQuestionRepository;
import com.tgrznar.javalearningapp.testsupport.EnrolledClassFactory;
import com.tgrznar.javalearningapp.testsupport.TestcontainersConfiguration;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.userprogress.UserProgress;
import com.tgrznar.javalearningapp.userprogress.UserProgressRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** Loading a quiz is limited per student: every load draws a new quiz and issues a new token. */
@SpringBootTest(properties = "app.rate-limit.quiz-load-per-user.max=3")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
class StudentQuizLoadRateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CourseModuleRepository moduleRepository;

    @Autowired
    private QuizQuestionRepository questionRepository;

    @Autowired
    private UserProgressRepository progressRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private EnrolledClassFactory enrolledClasses;

    private ResultActions load(User student, Long moduleId) throws Exception {
        String token = jwtService.generateAccessToken(student);
        return mockMvc.perform(get("/api/v1/learning/modules/" + moduleId + "/quiz")
                .header("Authorization", "Bearer " + token));
    }

    private CourseModule moduleWithQuiz() {
        CourseModule module = new CourseModule();
        module.setTitle("First");
        module.setDescription("Description");
        module.setTheoryContent("# Theory");
        module.setOrderNumber(1);
        module.setActive(true);
        module = moduleRepository.saveAndFlush(module);

        QuizQuestion question = new QuizQuestion();
        question.setModule(module);
        question.setQuestionText("Question");
        question.setOptionA("A");
        question.setOptionB("B");
        question.setOptionC("C");
        question.setOptionD("D");
        question.setCorrectOption(QuizOption.A);
        questionRepository.saveAndFlush(question);
        return module;
    }

    private User studentWithTheory(CourseModule module) {
        User student = enrolledClasses.saveEnrolledStudent();
        UserProgress progress = new UserProgress();
        progress.setUser(student);
        progress.setModule(module);
        progress.setTheoryCompleted(true);
        progressRepository.saveAndFlush(progress);
        return student;
    }

    @Test
    void load_overTheLimit_returns429WithRetryAfter() throws Exception {
        CourseModule module = moduleWithQuiz();
        User student = studentWithTheory(module);

        for (int i = 0; i < 3; i++) {
            load(student, module.getId()).andExpect(status().isOk());
        }

        load(student, module.getId())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void limit_isPerStudent() throws Exception {
        CourseModule module = moduleWithQuiz();
        User busy = studentWithTheory(module);
        User other = studentWithTheory(module);
        for (int i = 0; i < 3; i++) {
            load(busy, module.getId()).andExpect(status().isOk());
        }

        load(busy, module.getId()).andExpect(status().isTooManyRequests());
        load(other, module.getId()).andExpect(status().isOk());
    }
}