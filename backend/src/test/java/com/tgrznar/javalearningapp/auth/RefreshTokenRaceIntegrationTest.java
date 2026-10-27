package com.tgrznar.javalearningapp.auth;

import com.jayway.jsonpath.JsonPath;
import com.tgrznar.javalearningapp.testsupport.TestcontainersConfiguration;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A refresh token can be exchanged once, also when two requests with the same token arrive at the same
 * moment: exactly one gets a new token, the other one gets 401, and the user ends with one active token.
 * <p>
 * Deliberately NOT @Transactional (unlike the other integration tests): requests running in parallel
 * threads each have their own transaction and must see committed data. So the user is committed and
 * removed by hand in tearDown (his refresh tokens go with him, ON DELETE CASCADE).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class RefreshTokenRaceIntegrationTest {

    private static final String PASSWORD = "heslo12345";
    private static final int ROUNDS = 5;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String email;

    @BeforeEach
    void setUp() throws Exception {
        email = "refresh-race-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Jano\",\"surname\":\"Novak\",\"email\":\"" + email
                                + "\",\"password\":\"" + PASSWORD + "\",\"confirmPassword\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());
    }

    @AfterEach
    void tearDown() {
        userRepository.findByEmail(email).ifPresent(userRepository::delete);
    }

    @Test
    void sameRefreshTokenSentAtTheSameTime_isExchangedExactlyOnce() throws Exception {
        String token = login();
        assertThat(activeTokens()).isEqualTo(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 1; round <= ROUNDS; round++) {
                String current = token;
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch go = new CountDownLatch(1);

                List<Future<MockHttpServletResponse>> responses = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    responses.add(pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        return refresh(current);
                    }));
                }
                ready.await();
                go.countDown();

                List<MockHttpServletResponse> results = new ArrayList<>();
                for (Future<MockHttpServletResponse> response : responses) {
                    results.add(response.get(30, TimeUnit.SECONDS));
                }

                assertThat(results).extracting(MockHttpServletResponse::getStatus)
                        .as("round %d", round).containsExactlyInAnyOrder(200, 401);
                MockHttpServletResponse winner = results.stream()
                        .filter(result -> result.getStatus() == 200).findFirst().orElseThrow();
                MockHttpServletResponse loser = results.stream()
                        .filter(result -> result.getStatus() == 401).findFirst().orElseThrow();
                assertThat((String) JsonPath.read(loser.getContentAsString(), "$.code"))
                        .isEqualTo("INVALID_REFRESH_TOKEN");

                token = JsonPath.read(winner.getContentAsString(), "$.refreshToken");
                // One token was exchanged for one new token, never for two.
                assertThat(activeTokens()).as("active tokens after round %d", round).isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------- helpers ----------

    private String login() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.refreshToken");
    }

    private MockHttpServletResponse refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andReturn().getResponse();
    }

    private int activeTokens() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens rt JOIN users u ON u.id = rt.user_id "
                        + "WHERE u.email = ? AND rt.revoked = false",
                Integer.class, email);
        return count == null ? 0 : count;
    }
}