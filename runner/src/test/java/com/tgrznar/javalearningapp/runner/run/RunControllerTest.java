package com.tgrznar.javalearningapp.runner.run;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tgrznar.javalearningapp.runner.sandbox.SandboxRequest;
import com.tgrznar.javalearningapp.runner.sandbox.docker.DockerHealth;
import com.tgrznar.javalearningapp.runner.sandbox.exception.SandboxBusyException;
import com.tgrznar.javalearningapp.runner.sandbox.exception.SandboxUnavailableException;
import com.tgrznar.javalearningapp.runner.sandbox.result.CaseResult;
import com.tgrznar.javalearningapp.runner.sandbox.result.CaseStatus;
import com.tgrznar.javalearningapp.runner.sandbox.result.CompileResult;
import com.tgrznar.javalearningapp.runner.security.RunnerTokenFilter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/** POST /run with a mocked service: authentication, validation, and the mapping of failures to statuses. */
@SpringBootTest(properties = "runner.token=" + RunControllerTest.TOKEN)
@AutoConfigureMockMvc
class RunControllerTest {

    static final String TOKEN = "test-only-token-0123456789-0123456789-0123456789";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RunService runService;

    @MockitoBean
    private DockerHealth dockerHealth;

    private static MockHttpServletRequestBuilder run(String json) {
        return post("/run").header(RunnerTokenFilter.HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private static String json(String solution, String cases) {
        return "{\"solutionSource\":" + solution + ",\"caseInputs\":" + cases + "}";
    }

    @Test
    void run_withoutToken_returns401AndNeverReachesTheService() throws Exception {
        mockMvc.perform(post("/run").contentType(MediaType.APPLICATION_JSON)
                        .content(json("\"class Solution {}\"", "[]")))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(runService);
    }

    @Test
    void run_valid_returnsTheResultOfTheService() throws Exception {
        when(runService.run(any())).thenReturn(new RunResponse(new CompileResult(true, false, ""),
                List.of(new CaseResult(1, CaseStatus.OK, 12, "9\n", ""))));

        mockMvc.perform(run("{\"solutionSource\":\"class Solution {}\",\"driverSource\":\"class Main {}\","
                        + "\"caseInputs\":[\"3\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compile.success").value(true))
                .andExpect(jsonPath("$.cases[0].status").value("OK"))
                .andExpect(jsonPath("$.cases[0].stdout").value("9\n"));

        ArgumentCaptor<SandboxRequest> captor = ArgumentCaptor.forClass(SandboxRequest.class);
        verify(runService).run(captor.capture());
        assertThat(captor.getValue().solutionSource()).isEqualTo("class Solution {}");
        assertThat(captor.getValue().driverSource()).isEqualTo("class Main {}");
        assertThat(captor.getValue().caseInputs()).containsExactly("3");
    }

    @Test
    void run_withoutCaseInputs_isAccepted() throws Exception {
        when(runService.run(any())).thenReturn(new RunResponse(new CompileResult(true, false, ""), List.of()));

        mockMvc.perform(run("{\"solutionSource\":\"class Solution {}\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void run_blankSolution_returns400() throws Exception {
        mockMvc.perform(run(json("\"  \"", "[]")))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"INVALID_REQUEST\"}"));

        verifyNoInteractions(runService);
    }

    @Test
    void run_tooManyCases_returns400() throws Exception {
        String cases = "[" + "\"1\",".repeat(20) + "\"1\"]";

        mockMvc.perform(run(json("\"class Solution {}\"", cases)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(runService);
    }

    @Test
    void run_nullCaseInput_returns400() throws Exception {
        mockMvc.perform(run(json("\"class Solution {}\"", "[null]")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void run_malformedJson_returns400() throws Exception {
        mockMvc.perform(run("{\"solutionSource\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"INVALID_REQUEST\"}"));
    }

    @Test
    void run_inputOverTheSandboxLimits_returns400WithoutDetails() throws Exception {
        when(runService.run(any())).thenThrow(new IllegalArgumentException("Solution is too large"));

        mockMvc.perform(run(json("\"class Solution {}\"", "[]")))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"INVALID_REQUEST\"}"));
    }

    @Test
    void run_sandboxBusy_returns503WithRetryAfter() throws Exception {
        when(runService.run(any())).thenThrow(new SandboxBusyException());

        mockMvc.perform(run(json("\"class Solution {}\"", "[]")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", RunExceptionHandler.RETRY_AFTER_SECONDS))
                .andExpect(content().json("{\"error\":\"SANDBOX_BUSY\"}"));
    }

    @Test
    void run_sandboxUnavailable_returns500WithoutTheReason() throws Exception {
        when(runService.run(any())).thenThrow(
                new SandboxUnavailableException("Docker exited with 125 for jla-sbx-1-abcdef01: secret detail"));

        mockMvc.perform(run(json("\"class Solution {}\"", "[]")))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json("{\"error\":\"SANDBOX_UNAVAILABLE\"}"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }
}
