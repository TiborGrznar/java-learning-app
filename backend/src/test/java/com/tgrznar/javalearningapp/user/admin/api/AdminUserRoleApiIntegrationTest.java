package com.tgrznar.javalearningapp.user.admin.api;

import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PATCH /api/v1/users/{id}/role: an administrator changes the role of a user. */
class AdminUserRoleApiIntegrationTest extends AdminUserApiTestSupport {

    @Test
    void asTeacher_returns403AndChangesNothing() throws Exception {
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT);

        as(patch(url(target.getId()) + "/role").contentType(MediaType.APPLICATION_JSON)
                .content(roleJson("ADMIN", null)), UserRole.TEACHER)
                .andExpect(status().isForbidden());

        assertThat(userRepository.findById(target.getId()).orElseThrow().getRole()).isEqualTo(UserRole.STUDENT);
    }

    @Test
    void studentToTeacher_returns200() throws Exception {
        School school = saveSchool(true);
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT);

        as(patch(url(target.getId()) + "/role").contentType(MediaType.APPLICATION_JSON)
                .content(roleJson("TEACHER", school.getId())), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andExpect(jsonPath("$.schoolId").value(school.getId()));
    }

    @Test
    void toTeacherWithoutSchool_returns400WithFieldError() throws Exception {
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT);

        as(patch(url(target.getId()) + "/role").contentType(MediaType.APPLICATION_JSON)
                .content(roleJson("TEACHER", null)), UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.schoolId").exists());
    }

    @Test
    void ownAccount_returns409() throws Exception {
        User admin = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.ADMIN);

        asUser(patch(url(admin.getId()) + "/role").contentType(MediaType.APPLICATION_JSON)
                .content(roleJson("STUDENT", null)), admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_MODIFICATION_NOT_ALLOWED"));

        assertThat(userRepository.findById(admin.getId()).orElseThrow().getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void lastActiveAdmin_returns409() throws Exception {
        User actor = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.ADMIN);
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.ADMIN);
        makeOnlyActiveAdmin(target);

        asUser(patch(url(target.getId()) + "/role").contentType(MediaType.APPLICATION_JSON)
                .content(roleJson("STUDENT", null)), actor)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN"));

        assertThat(userRepository.findById(target.getId()).orElseThrow().getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void missingRole_returns400WithFieldError() throws Exception {
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT);

        as(patch(url(target.getId()) + "/role").contentType(MediaType.APPLICATION_JSON).content("{}"),
                UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.role").exists());
    }

    @Test
    void unknownUser_returns404() throws Exception {
        as(patch(url(999999999L) + "/role").contentType(MediaType.APPLICATION_JSON)
                .content(roleJson("STUDENT", null)), UserRole.ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    private static String roleJson(String role, Long schoolId) {
        return "{\"role\":\"" + role + "\",\"schoolId\":" + schoolId + "}";
    }
}