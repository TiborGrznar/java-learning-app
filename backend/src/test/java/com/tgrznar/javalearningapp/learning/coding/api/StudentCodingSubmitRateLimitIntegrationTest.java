package com.tgrznar.javalearningapp.learning.coding.api;

import com.tgrznar.javalearningapp.auth.security.JwtService;
import com.tgrznar.javalearningapp.codingtask.CodingTask;
import com.tgrznar.javalearningapp.codingtask.CodingTaskCase;
import com.tgrznar.javalearningapp.codingtask.CodingTaskCaseRepository;
import com.tgrznar.javalearningapp.codingtask.CodingTaskRepository;
import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.module.CourseModuleRepository;
import com.tgrznar.javalearningapp.testsupport.EnrolledClassFactory;
import com.tgrznar.javalearningapp.testsupport.FakeCodeRunner;
import com.tgrznar.javalearningapp.testsupport.FakeCodeRunnerConfig;
import com.tgrznar.javalearningapp.testsupport.TestcontainersConfiguration;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.userprogress.UserProgress;
import com.tgrznar.javalearningapp.userprogress.UserProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Submitting is limited per student: every submission occupies a container of the sandbox. */
@SpringBootTest(properties = "app.rate-limit.coding-submit-per-user.max=2")
@Import({TestcontainersConfiguration.class, FakeCodeRunnerConfig.class})
@AutoConfigureMockMvc
@Transactional
class StudentCodingSubmitRateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CourseModuleRepository moduleRepository;

    @Autowired
    private CodingTaskRepository taskRepository;

    @Autowired
    private CodingTaskCaseRepository caseRepository;

    @Autowired
    private UserProgressRepository progressRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private EnrolledClassFactory enrolledClasses;

    @Autowired
    private FakeCodeRunner runner;

    @BeforeEach
    void resetRunner() {
        runner.reset();
        runner.respondWith(FakeCodeRunner.okWith("4\n"));
    }

    @Test
    void submit_overTheLimit_returns429WithRetryAfter_andTheRunnerIsNotCalledAgain() throws Exception {
        CourseModule module = saveModule();
        CodingTask task = saveTaskWithCase(module);
        User student = studentWithTheory(module);
        User other = studentWithTheory(module);

        submit(module, task, student).andExpect(status().isOk());
        submit(module, task, student).andExpect(status().isOk());
        submit(module, task, student)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

        assertThat(runner.calls()).hasSize(2);
        // The limit is per student: another student is not affected.
        submit(module, task, other).andExpect(status().isOk());
    }

    private ResultActions submit(CourseModule module, CodingTask task, User student) throws Exception {
        String token = jwtService.generateAccessToken(student);
        return mockMvc.perform(post("/api/v1/learning/modules/" + module.getId() + "/coding-tasks/" + task.getId() + "/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"class Solution {}\"}")
                .header("Authorization", "Bearer " + token));
    }

    private CourseModule saveModule() {
        CourseModule module = new CourseModule();
        module.setTitle("Module");
        module.setDescription("Description");
        module.setTheoryContent("# Theory");
        module.setOrderNumber(1);
        module.setActive(true);
        return moduleRepository.saveAndFlush(module);
    }

    private CodingTask saveTaskWithCase(CourseModule module) {
        CodingTask task = new CodingTask();
        task.setModule(module);
        task.setOrderNumber(1);
        task.setTitle("Task");
        task.setActive(true);
        task = taskRepository.saveAndFlush(task);

        CodingTaskCase testCase = new CodingTaskCase();
        testCase.setCodingTask(task);
        testCase.setOrderNumber(1);
        testCase.setInputText("2\n");
        testCase.setExpectedOutput("4\n");
        testCase.setHidden(false);
        caseRepository.saveAndFlush(testCase);
        return task;
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
}
