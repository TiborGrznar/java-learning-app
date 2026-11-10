package com.tgrznar.javalearningapp.user.admin.api;

import com.jayway.jsonpath.JsonPath;
import com.tgrznar.javalearningapp.auth.token.RefreshTokenService;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /api/v1/users/{id}/password-reset: an administrator gives a user a new temporary password. */
class AdminUserPasswordResetApiIntegrationTest extends AdminUserApiTestSupport {

    private static final String OLD_PASSWORD = "stare-heslo-1";

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RefreshTokenService refreshTokenService;

    // ---------- access ----------

    @Test
    void anonymous_returns401() throws Exception {
        User target = savedTarget(UserRole.STUDENT);

        mockMvc.perform(post(resetUrl(target)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void asTeacher_returns403AndChangesNothing() throws Exception {
        User target = savedTarget(UserRole.STUDENT);

        as(post(resetUrl(target)), UserRole.TEACHER)
                .andExpect(status().isForbidden());

        flushAndClear();
        assertThat(passwordEncoder.matches(OLD_PASSWORD, hashOf(target))).isTrue();
    }

    @Test
    void asStudent_returns403AndChangesNothing() throws Exception {
        User target = savedTarget(UserRole.STUDENT);

        as(post(resetUrl(target)), UserRole.STUDENT)
                .andExpect(status().isForbidden());

        flushAndClear();
        assertThat(passwordEncoder.matches(OLD_PASSWORD, hashOf(target))).isTrue();
    }

    // ---------- success ----------

    @Test
    void resetsStudent_answersOnceWithTheTemporaryPassword_andNoCache() throws Exception {
        User target = savedTarget(UserRole.STUDENT);

        String response = as(post(resetUrl(target)), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.user.id").value(target.getId()))
                .andExpect(jsonPath("$.user.email").value(target.getEmail()))
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String temporaryPassword = JsonPath.read(response, "$.temporaryPassword");

        assertThat(temporaryPassword).hasSize(16);
        flushAndClear();
        assertThat(passwordEncoder.matches(temporaryPassword, hashOf(target))).isTrue();
        assertThat(passwordEncoder.matches(OLD_PASSWORD, hashOf(target))).isFalse();
    }

    @Test
    void theTemporaryPasswordWorksForLogin_andTheOldOneDoesNot() throws Exception {
        User target = savedTarget(UserRole.STUDENT);

        String temporaryPassword = resetAndGetPassword(target);

        login(target.getEmail(), temporaryPassword).andExpect(status().isOk());
        login(target.getEmail(), OLD_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void resetsTeacher() throws Exception {
        User target = savedTarget(UserRole.TEACHER);

        String temporaryPassword = resetAndGetPassword(target);

        login(target.getEmail(), temporaryPassword).andExpect(status().isOk());
    }

    @Test
    void resetsAnotherAdministrator() throws Exception {
        User target = savedTarget(UserRole.ADMIN);

        String temporaryPassword = resetAndGetPassword(target);

        login(target.getEmail(), temporaryPassword).andExpect(status().isOk());
    }

    @Test
    void resetsDeactivatedAccount_whichStaysDeactivated() throws Exception {
        User target = savedTarget(UserRole.STUDENT);
        target.setActive(false);
        userRepository.saveAndFlush(target);

        String response = as(post(resetUrl(target)), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.active").value(false))
                .andReturn().getResponse().getContentAsString();
        String temporaryPassword = JsonPath.read(response, "$.temporaryPassword");

        flushAndClear();
        assertThat(passwordEncoder.matches(temporaryPassword, hashOf(target))).isTrue();
        // After a reactivation the temporary password is the one that works; until then the login is refused.
        login(target.getEmail(), temporaryPassword)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void revokesEveryRefreshTokenOfTheUser() throws Exception {
        User target = savedTarget(UserRole.STUDENT);
        String firstDevice = refreshTokenService.issue(target);
        String secondDevice = refreshTokenService.issue(target);

        resetAndGetPassword(target);

        // The bulk revoke bypasses the persistence context, so forget everything loaded before.
        flushAndClear();
        refresh(firstDevice).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        refresh(secondDevice).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void everyResetGeneratesANewPassword_andOnlyTheLastOneWorks() throws Exception {
        User target = savedTarget(UserRole.STUDENT);

        String first = resetAndGetPassword(target);
        String second = resetAndGetPassword(target);

        assertThat(second).isNotEqualTo(first);
        login(target.getEmail(), second).andExpect(status().isOk());
        login(target.getEmail(), first).andExpect(status().isUnauthorized());
    }

    @Test
    void doesNotTouchOtherUsers() throws Exception {
        User target = savedTarget(UserRole.STUDENT);
        User other = savedTarget(UserRole.STUDENT);
        String otherRefreshToken = refreshTokenService.issue(other);

        resetAndGetPassword(target);

        flushAndClear();
        assertThat(passwordEncoder.matches(OLD_PASSWORD, hashOf(other))).isTrue();
        refresh(otherRefreshToken).andExpect(status().isOk());
    }

    @Test
    void responseNeverContainsAHash() throws Exception {
        User target = savedTarget(UserRole.STUDENT);
        String oldHash = hashOf(target);

        String response = as(post(resetUrl(target)), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        flushAndClear();
        assertThat(response).doesNotContain("passwordHash", "password_hash", oldHash, hashOf(target));
    }

    // ---------- rejected ----------

    @Test
    void ownAccount_returns409AndKeepsThePassword() throws Exception {
        User admin = savedTarget(UserRole.ADMIN);

        asUser(post(resetUrl(admin)), admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_MODIFICATION_NOT_ALLOWED"));

        flushAndClear();
        assertThat(passwordEncoder.matches(OLD_PASSWORD, hashOf(admin))).isTrue();
    }

    @Test
    void unknownUser_returns404() throws Exception {
        as(post(URL + "/999999999/password-reset"), UserRole.ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void nonNumericId_returns400() throws Exception {
        as(post(URL + "/abc/password-reset"), UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    // ---------- helpers ----------

    /** A saved user whose password is {@link #OLD_PASSWORD}, with a real BCrypt hash. */
    private User savedTarget(UserRole role) {
        User user = saveUser(uniqueEmail().toLowerCase(java.util.Locale.ROOT), role);
        user.setPasswordHash(passwordEncoder.encode(OLD_PASSWORD));
        return userRepository.saveAndFlush(user);
    }

    private String resetAndGetPassword(User target) throws Exception {
        String response = as(post(resetUrl(target)), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.temporaryPassword");
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    private static String resetUrl(User target) {
        return url(target.getId()) + "/password-reset";
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
