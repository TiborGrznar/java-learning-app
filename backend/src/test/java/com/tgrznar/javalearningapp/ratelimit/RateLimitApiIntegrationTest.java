package com.tgrznar.javalearningapp.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tgrznar.javalearningapp.testsupport.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * The limiters are singletons shared by all tests of this class, so every test uses its own
 * client addresses and e-mails and never depends on the state left by another test.
 */
@SpringBootTest(properties = {
        "app.rate-limit.login-per-ip.max=3",
        "app.rate-limit.login-failures-per-email.max=3"})
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
class RateLimitApiIntegrationTest {

    private static final String PASSWORD = "Correct-horse-battery-1";

    @Autowired
    private MockMvc mockMvc;

    private ResultActions login(String email, String password, String ip) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    void login_fromOneIpIsLimitedAndTheAnswerHasRetryAfter() throws Exception {
        for (int i = 0; i < 3; i++) {
            login(uniqueEmail(), "wrong-password-1", "10.1.0.1").andExpect(status().isUnauthorized());
        }

        login(uniqueEmail(), "wrong-password-1", "10.1.0.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void failedLogins_forOneEmailAreLimitedWhateverTheIp() throws Exception {
        String email = uniqueEmail();
        login(email, "wrong-password-1", "10.2.0.1").andExpect(status().isUnauthorized());
        login(email, "wrong-password-1", "10.2.0.2").andExpect(status().isUnauthorized());
        login(email, "wrong-password-1", "10.2.0.3").andExpect(status().isUnauthorized());

        login(email, "wrong-password-1", "10.2.0.4")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        // Another e-mail from yet another address is not affected.
        login(uniqueEmail(), "wrong-password-1", "10.2.0.5").andExpect(status().isUnauthorized());
    }

    @Test
    void successfulLogin_clearsTheFailureCounter() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(request -> {
                            request.setRemoteAddr("10.3.0.1");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                                + "\",\"confirmPassword\":\"" + PASSWORD
                                + "\",\"name\":\"Jano\",\"surname\":\"Novak\"}"))
                .andExpect(status().isCreated());

        login(email, "wrong-password-1", "10.3.0.2").andExpect(status().isUnauthorized());
        login(email, "wrong-password-1", "10.3.0.3").andExpect(status().isUnauthorized());
        login(email, PASSWORD, "10.3.0.4").andExpect(status().isOk());

        // Without the reset the third and fourth failure would be blocked (2 + 2 > 3).
        login(email, "wrong-password-1", "10.3.0.5").andExpect(status().isUnauthorized());
        login(email, "wrong-password-1", "10.3.0.6").andExpect(status().isUnauthorized());
    }

    @Test
    void bodyOverTheLimit_isRefusedWith413() throws Exception {
        String body = "{\"email\":\"" + "a".repeat(20_000) + "\",\"password\":\"x\"}";

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("10.4.0.1");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }
}