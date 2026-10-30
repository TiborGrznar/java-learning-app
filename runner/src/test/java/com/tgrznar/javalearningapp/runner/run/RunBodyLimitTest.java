package com.tgrznar.javalearningapp.runner.run;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tgrznar.javalearningapp.runner.sandbox.docker.DockerHealth;
import com.tgrznar.javalearningapp.runner.security.RunnerTokenFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The body limit on the real filter chain, with the smallest archive limit (1 KiB -> about 22 KiB of JSON). */
@SpringBootTest(properties = {
        "runner.token=" + RunBodyLimitTest.TOKEN,
        "runner.sandbox.capacity.max-archive-bytes=1024"})
@AutoConfigureMockMvc
class RunBodyLimitTest {

    static final String TOKEN = "test-only-token-0123456789-0123456789-0123456789";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RunService runService;

    @MockitoBean
    private DockerHealth dockerHealth;

    @Test
    void run_bodyOverTheLimit_returns413BeforeTheServiceIsCalled() throws Exception {
        String body = "{\"solutionSource\":\"" + "x".repeat(30_000) + "\"}";

        mockMvc.perform(post("/run").header(RunnerTokenFilter.HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(413))
                .andExpect(content().json("{\"error\":\"PAYLOAD_TOO_LARGE\"}"));

        verifyNoInteractions(runService);
    }

    @Test
    void run_oversizedBodyWithoutToken_returns401NotTheLimitError() throws Exception {
        String body = "{\"solutionSource\":\"" + "x".repeat(30_000) + "\"}";

        mockMvc.perform(post("/run").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }
}
