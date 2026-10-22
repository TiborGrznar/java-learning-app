package com.tgrznar.javalearningapp.runner.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The token filter and the health endpoint. There is no protected endpoint yet, so "the filter let
 * the request through" shows as 404 (nothing is mapped there); real endpoint tests replace that
 * with the endpoint's own response when /run is added.
 */
@SpringBootTest(properties = "runner.token=" + RunnerTokenFilterTest.TOKEN)
@AutoConfigureMockMvc
class RunnerTokenFilterTest {

    static final String TOKEN = "test-only-token-0123456789-0123456789-0123456789";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void health_needsNoToken() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void protectedPath_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/run"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"error\":\"UNAUTHORIZED\"}"));
    }

    @Test
    void protectedPath_withWrongToken_returns401() throws Exception {
        mockMvc.perform(post("/run").header(RunnerTokenFilter.HEADER, TOKEN + "x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedPath_withEmptyToken_returns401() throws Exception {
        mockMvc.perform(post("/run").header(RunnerTokenFilter.HEADER, ""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedPath_withCorrectToken_passesTheFilter() throws Exception {
        mockMvc.perform(post("/run").header(RunnerTokenFilter.HEADER, TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void health_withOtherMethodOrPath_isStillProtected() throws Exception {
        mockMvc.perform(post("/health")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/health/")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }
}