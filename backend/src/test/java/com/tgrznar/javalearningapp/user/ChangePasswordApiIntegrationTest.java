package com.tgrznar.javalearningapp.user;

import com.jayway.jsonpath.JsonPath;
import com.tgrznar.javalearningapp.auth.security.JwtService;
import com.tgrznar.javalearningapp.auth.token.RefreshTokenService;
import com.tgrznar.javalearningapp.testsupport.TestUsers;
import com.tgrznar.javalearningapp.testsupport.TestcontainersConfiguration;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.user.model.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PUT /api/v1/users/me/password against a real MySQL. The tests use real BCrypt hashes and the real
 * login and refresh endpoints, because the point of a password change is what happens to them afterwards.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
class ChangePasswordApiIntegrationTest {

    private static final String URL = "/api/v1/users/me/password";
    private static final String CURRENT = "stare-heslo-1";
    private static final String NEW = "nove-heslo-22";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestUsers testUsers;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ---------- access ----------

    @Test
    void changePassword_withoutToken_returns401() throws Exception {
        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(body(CURRENT, NEW, NEW)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void changePassword_worksForEveryRole() throws Exception {
        for (UserRole role : UserRole.values()) {
            User user = savedUser(role);

            change(user, body(CURRENT, NEW, NEW))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value(role.name()));
        }
    }

    // ---------- success ----------

    @Test
    void changePassword_replacesThePasswordAndAnswersWithNewTokensThatMustNotBeCached() throws Exception {
        User user = savedUser(UserRole.STUDENT);

        change(user, body(CURRENT, NEW, NEW))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));

        flushAndClear();
        String hash = userRepository.findById(user.getId()).orElseThrow().getPasswordHash();
        assertThat(passwordEncoder.matches(NEW, hash)).isTrue();
        assertThat(passwordEncoder.matches(CURRENT, hash)).isFalse();

        login(user.getEmail(), NEW).andExpect(status().isOk());
        login(user.getEmail(), CURRENT)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void changePassword_revokesEveryRefreshTokenAndTheNewOneWorks() throws Exception {
        User user = savedUser(UserRole.STUDENT);
        String firstDevice = refreshTokenService.issue(user);
        String secondDevice = refreshTokenService.issue(user);

        String response = change(user, body(CURRENT, NEW, NEW))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newRefreshToken = JsonPath.read(response, "$.refreshToken");

        // The bulk revoke bypasses the persistence context, so forget everything loaded before.
        flushAndClear();
        refresh(firstDevice).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        refresh(secondDevice).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        refresh(newRefreshToken).andExpect(status().isOk());
    }

    @Test
    void changePassword_acceptsNewPasswordOfExactly72BytesWithDiacritics() throws Exception {
        User user = savedUser(UserRole.STUDENT);
        // 36 characters of 2 bytes each
        String longPassword = "á".repeat(36);

        change(user, body(CURRENT, longPassword, longPassword)).andExpect(status().isOk());

        login(user.getEmail(), longPassword).andExpect(status().isOk());
    }

    @Test
    void changePassword_changesOnlyTheCallersPassword_whateverTheBodySays() throws Exception {
        User caller = savedUser(UserRole.STUDENT);
        User other = savedUser(UserRole.STUDENT);
        String otherRefreshToken = refreshTokenService.issue(other);
        String bodyWithForeignId = """
                {"userId":%d,"currentPassword":"%s","newPassword":"%s","confirmNewPassword":"%s"}
                """.formatted(other.getId(), CURRENT, NEW, NEW);

        change(caller, bodyWithForeignId).andExpect(status().isOk());

        flushAndClear();
        String otherHash = userRepository.findById(other.getId()).orElseThrow().getPasswordHash();
        assertThat(passwordEncoder.matches(CURRENT, otherHash)).isTrue();
        // and the tokens of the other user are untouched
        refresh(otherRefreshToken).andExpect(status().isOk());
    }

    @Test
    void changePassword_neverEchoesAPassword() throws Exception {
        User user = savedUser(UserRole.STUDENT);

        String response = change(user, body(CURRENT, NEW, NEW))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(CURRENT, NEW);
    }

    // ---------- rejected ----------

    @Test
    void changePassword_wrongCurrentPassword_returns400AndChangesNothing() throws Exception {
        User user = savedUser(UserRole.STUDENT);
        String refreshToken = refreshTokenService.issue(user);

        change(user, body("zle-heslo-9", NEW, NEW))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));

        flushAndClear();
        assertThat(passwordEncoder.matches(CURRENT, hashOf(user))).isTrue();
        refresh(refreshToken).andExpect(status().isOk());
    }

    @Test
    void changePassword_currentPasswordOver72Bytes_returns400NotAServerError() throws Exception {
        User user = savedUser(UserRole.STUDENT);

        change(user, body("á".repeat(40), NEW, NEW))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
    }

    @Test
    void changePassword_differentConfirmation_returns400() throws Exception {
        User user = savedUser(UserRole.STUDENT);

        change(user, body(CURRENT, NEW, "ine-heslo-333"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));

        flushAndClear();
        assertThat(passwordEncoder.matches(CURRENT, hashOf(user))).isTrue();
    }

    @Test
    void changePassword_newPasswordEqualToCurrent_returns400() throws Exception {
        User user = savedUser(UserRole.STUDENT);

        change(user, body(CURRENT, CURRENT, CURRENT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_UNCHANGED"));
    }

    @Test
    void changePassword_tooShortNewPassword_returnsFieldError() throws Exception {
        User user = savedUser(UserRole.STUDENT);

        change(user, body(CURRENT, "abc12", "abc12"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());
    }

    @Test
    void changePassword_newPasswordOverTheByteLimit_returnsFieldError() throws Exception {
        User user = savedUser(UserRole.STUDENT);
        // 37 characters, 74 bytes
        String tooLong = "á".repeat(37);

        change(user, body(CURRENT, tooLong, tooLong))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());
    }

    @Test
    void changePassword_emptyBody_returnsFieldErrorsForAllThreePasswords() throws Exception {
        User user = savedUser(UserRole.STUDENT);

        change(user, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.currentPassword").exists())
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists())
                .andExpect(jsonPath("$.fieldErrors.confirmNewPassword").exists());
    }

    @Test
    void changePassword_deactivatedAccountWithAStillValidToken_returns403() throws Exception {
        User user = savedUser(UserRole.STUDENT);
        String token = jwtService.generateAccessToken(user);
        user.setActive(false);
        userRepository.saveAndFlush(user);

        mockMvc.perform(put(URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(CURRENT, NEW, NEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));

        flushAndClear();
        assertThat(passwordEncoder.matches(CURRENT, hashOf(user))).isTrue();
    }

    @Test
    void changePassword_tokenOfADeletedUser_returns401() throws Exception {
        User user = savedUser(UserRole.STUDENT);
        String token = jwtService.generateAccessToken(user);
        userRepository.delete(user);
        userRepository.flush();

        mockMvc.perform(put(URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(CURRENT, NEW, NEW)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    // ---------- helpers ----------

    /** A saved user whose password is {@link #CURRENT}, with a real BCrypt hash. */
    private User savedUser(UserRole role) {
        User user = testUsers.user(role).save();
        user.setPasswordHash(passwordEncoder.encode(CURRENT));
        return userRepository.saveAndFlush(user);
    }

    private ResultActions change(User actor, String json) throws Exception {
        return mockMvc.perform(put(URL)
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
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
                .content("""
                        {"refreshToken":"%s"}
                        """.formatted(refreshToken)));
    }

    private static String body(String current, String newPassword, String confirmation) {
        return """
                {"currentPassword":"%s","newPassword":"%s","confirmNewPassword":"%s"}
                """.formatted(current, newPassword, confirmation);
    }

    private String hashOf(User user) {
        return userRepository.findById(user.getId()).orElseThrow().getPasswordHash();
    }

    /** Writes pending changes and forgets every loaded entity, so the next read really goes to the database. */
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
