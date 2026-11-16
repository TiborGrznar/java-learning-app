package com.tgrznar.javalearningapp.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tgrznar.javalearningapp.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * CORS in the whole filter chain. The general limit is set to 3 requests so the test can reach a 429.
 * The limiters are shared by all tests of this class, so every test uses its own client address.
 */
@SpringBootTest(properties = {
        "app.cors.allowed-origins=https://frontend.test,http://localhost:5173",
        "app.rate-limit.api-per-ip.max=3"})
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class CorsApiIntegrationTest {

    private static final String ALLOWED = "https://frontend.test";

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private ResultActions getMe(String origin, String ip) throws Exception {
        return mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.ORIGIN, origin).with(from(ip)));
    }

    private ResultActions preflight(String origin, String method, String ip) throws Exception {
        return mockMvc.perform(options("/api/v1/users/me/password")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type")
                .with(from(ip)));
    }

    @Test
    void preflight_fromAllowedOrigin_isAnsweredWithTheCorsHeaders() throws Exception {
        String methods = preflight(ALLOWED, "PUT", "10.30.0.1")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600"))
                .andReturn().getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS);

        assertThat(methods).contains("PUT", "PATCH", "DELETE");
    }

    @Test
    void preflight_forAMethodThatIsNotAllowed_isRefused() throws Exception {
        preflight(ALLOWED, "TRACE", "10.30.0.2")
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void request_fromAnOriginThatIsNotListed_isRefusedWithoutCorsHeaders() throws Exception {
        getMe("https://evil.test", "10.30.0.3")
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void unauthenticatedAnswer_carriesTheCorsHeaders() throws Exception {
        // Without them the browser hides the 401 and the frontend cannot start its refresh.
        getMe(ALLOWED, "10.30.0.4")
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void rateLimitAnswer_carriesTheCorsHeadersAndRetryAfterIsReadable() throws Exception {
        String ip = "10.30.0.5";
        for (int i = 0; i < 3; i++) {
            getMe(ALLOWED, ip).andExpect(status().isUnauthorized());
        }

        getMe(ALLOWED, ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Retry-After"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void preflight_doesNotCountAgainstTheRateLimit() throws Exception {
        String ip = "10.30.0.6";
        for (int i = 0; i < 10; i++) {
            preflight(ALLOWED, "PUT", ip).andExpect(status().isOk());
        }

        getMe(ALLOWED, ip).andExpect(status().isUnauthorized());
    }
}
