package com.tgrznar.javalearningapp.user.admin.api;

import com.jayway.jsonpath.JsonPath;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PATCH /api/v1/users/{id}/active: an administrator deactivates or reactivates an account. */
class AdminUserActiveApiIntegrationTest extends AdminUserApiTestSupport {

    private static final String PASSWORD = "Password123!";

    @Test
    void asTeacher_returns403AndChangesNothing() throws Exception {
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT);

        as(patch(url(target.getId()) + "/active").contentType(MediaType.APPLICATION_JSON)
                .content(activeJson(false)), UserRole.TEACHER)
                .andExpect(status().isForbidden());

        assertThat(userRepository.findById(target.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void deactivatesUser() throws Exception {
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT);

        as(patch(url(target.getId()) + "/active").contentType(MediaType.APPLICATION_JSON).content(activeJson(false)),
                UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        assertThat(userRepository.findById(target.getId()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void reactivatesUser() throws Exception {
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT, null, "Alfa", false);

        as(patch(url(target.getId()) + "/active").contentType(MediaType.APPLICATION_JSON).content(activeJson(true)),
                UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void ownAccount_returns409() throws Exception {
        User admin = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.ADMIN);

        asUser(patch(url(admin.getId()) + "/active").contentType(MediaType.APPLICATION_JSON).content(activeJson(false)),
                admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_MODIFICATION_NOT_ALLOWED"));

        assertThat(userRepository.findById(admin.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void lastActiveAdmin_returns409() throws Exception {
        User actor = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.ADMIN);
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.ADMIN);
        makeOnlyActiveAdmin(target);

        asUser(patch(url(target.getId()) + "/active").contentType(MediaType.APPLICATION_JSON).content(activeJson(false)),
                actor)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN"));

        assertThat(userRepository.findById(target.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void missingFlag_returns400WithFieldError() throws Exception {
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT);

        as(patch(url(target.getId()) + "/active").contentType(MediaType.APPLICATION_JSON).content("{}"),
                UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.active").exists());
    }

    @Test
    void deactivatedUser_cannotLogIn() throws Exception {
        User target = saveUserWithPassword(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT);

        as(patch(url(target.getId()) + "/active").contentType(MediaType.APPLICATION_JSON).content(activeJson(false)),
                UserRole.ADMIN)
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(target.getEmail())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void deactivatedUser_cannotRefreshTheSession() throws Exception {
        User target = saveUserWithPassword(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT);

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(target.getEmail())))
                .andExpect(status().isOk())
                .andReturn();
        String refreshToken = JsonPath.read(login.getResponse().getContentAsString(), "$.refreshToken");

        as(patch(url(target.getId()) + "/active").contentType(MediaType.APPLICATION_JSON).content(activeJson(false)),
                UserRole.ADMIN)
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    private User saveUserWithPassword(String email, UserRole role) {
        User user = saveUser(email, role);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return userRepository.saveAndFlush(user);
    }

    private static String activeJson(boolean active) {
        return "{\"active\":" + active + "}";
    }

    private static String loginJson(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}";
    }
}