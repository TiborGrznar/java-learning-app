package com.tgrznar.javalearningapp.auth;

import com.jayway.jsonpath.JsonPath;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests of the authentication API against the real database.
 * Each test runs in a transaction that is rolled back, so no data is left behind.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthApiIntegrationTest {

    private static final String PASSWORD = "heslo12345";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    // ---------- register ----------

    @Test
    void register_thenLogin_returnsTokensForStudent() throws Exception {
        String email = uniqueEmail();

        register(email, PASSWORD, PASSWORD).andExpect(status().isCreated());

        login(email, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    void register_rejectsExistingEmail() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD, PASSWORD).andExpect(status().isCreated());

        register(email, PASSWORD, PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void register_rejectsMismatchedPasswords() throws Exception {
        register(uniqueEmail(), PASSWORD, "ine-heslo-123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));
    }

    @Test
    void register_returnsFieldErrorsForInvalidInput() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","surname":"Novak","email":"nie-je-email","password":"abc","confirmPassword":"abc"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void register_rejectsMalformedBody() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("toto nie je json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
    }

    // ---------- login ----------

    @Test
    void login_rejectsWrongPassword() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD, PASSWORD).andExpect(status().isCreated());

        login(email, "zle-heslo-123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void login_rejectsUnknownEmailWithSameErrorAsWrongPassword() throws Exception {
        login(uniqueEmail(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void login_rejectsDisabledAccount() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD, PASSWORD).andExpect(status().isCreated());

        User user = userRepository.findByEmail(email).orElseThrow();
        user.setActive(false);
        userRepository.saveAndFlush(user);

        login(email, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    // ---------- refresh and logout ----------

    @Test
    void refresh_rotatesTokenAndRejectsReuseOfTheOldOne() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD, PASSWORD).andExpect(status().isCreated());
        String oldRefreshToken = refreshTokenFrom(login(email, PASSWORD));

        String response = refresh(oldRefreshToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String newRefreshToken = JsonPath.read(response, "$.refreshToken");

        assertThat(newRefreshToken).isNotEqualTo(oldRefreshToken);

        refresh(oldRefreshToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        refresh(newRefreshToken).andExpect(status().isOk());
    }

    @Test
    void refresh_rejectsUnknownToken() throws Exception {
        refresh("neexistujuci-token")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void logout_revokesRefreshToken() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD, PASSWORD).andExpect(status().isCreated());
        String refreshToken = refreshTokenFrom(login(email, PASSWORD));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)))
                .andExpect(status().isNoContent());

        refresh(refreshToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void logout_succeedsForUnknownToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"neexistujuci-token\"}"))
                .andExpect(status().isNoContent());
    }

    // ---------- security configuration ----------

    @Test
    void protectedPath_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/some-protected-resource"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedPath_withTamperedToken_returns401() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD, PASSWORD).andExpect(status().isCreated());
        String accessToken = accessTokenFrom(login(email, PASSWORD));

        mockMvc.perform(get("/api/v1/some-protected-resource")
                        .header("Authorization", "Bearer " + accessToken + "x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedPath_withValidToken_passesSecurity() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD, PASSWORD).andExpect(status().isCreated());
        String accessToken = accessTokenFrom(login(email, PASSWORD));

        // No such endpoint exists, so a request that gets past security ends in 404, not 401.
        mockMvc.perform(get("/api/v1/some-protected-resource")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    // ---------- helpers ----------

    private String uniqueEmail() {
        return "student-" + UUID.randomUUID() + "@example.com";
    }

    private ResultActions register(String email, String password, String confirmPassword) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Jano","surname":"Novak","email":"%s","password":"%s","confirmPassword":"%s"}
                        """.formatted(email, password, confirmPassword)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password)));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)));
    }

    private String refreshTokenFrom(ResultActions loginResult) throws Exception {
        return JsonPath.read(bodyOf(loginResult), "$.refreshToken");
    }

    private String accessTokenFrom(ResultActions loginResult) throws Exception {
        return JsonPath.read(bodyOf(loginResult), "$.accessToken");
    }

    private String bodyOf(ResultActions result) throws Exception {
        return result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
}