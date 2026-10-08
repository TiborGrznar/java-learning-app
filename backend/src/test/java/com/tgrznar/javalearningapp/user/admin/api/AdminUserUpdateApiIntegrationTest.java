package com.tgrznar.javalearningapp.user.admin.api;

import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PUT /api/v1/users/{id}: an administrator edits name, surname, e-mail and school. */
class AdminUserUpdateApiIntegrationTest extends AdminUserApiTestSupport {

    @Test
    void asTeacher_returns403AndChangesNothing() throws Exception {
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT);

        as(put(url(target.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(updateJson("Hacked", "Hacked", uniqueEmail(), null)), UserRole.TEACHER)
                .andExpect(status().isForbidden());

        assertThat(userRepository.findById(target.getId()).orElseThrow().getName()).isEqualTo("Jano");
    }

    @Test
    void changesFieldsAndNormalizesEmail() throws Exception {
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT);
        String newEmail = "New-" + UUID.randomUUID() + "@Example.COM";

        as(put(url(target.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(updateJson(" Peter ", " Horvath ", newEmail, null)), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Peter"))
                .andExpect(jsonPath("$.surname").value("Horvath"))
                .andExpect(jsonPath("$.email").value(newEmail.toLowerCase(Locale.ROOT)))
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    void teacherCanMoveToAnotherActiveSchool() throws Exception {
        School oldSchool = saveSchool(true);
        School newSchool = saveSchool(true);
        User teacher = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.TEACHER, oldSchool, "Alfa", true);

        as(put(url(teacher.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(updateJson("Jana", "Novakova", teacher.getEmail(), newSchool.getId())), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schoolId").value(newSchool.getId()))
                .andExpect(jsonPath("$.schoolName").value(newSchool.getName()));
    }

    @Test
    void keepingOwnEmail_isNotADuplicate() throws Exception {
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT);

        as(put(url(target.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(updateJson("Peter", "Horvath", target.getEmail(), null)), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(target.getEmail()));
    }

    @Test
    void emailOfAnotherUser_returns409() throws Exception {
        User other = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT);
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT);

        as(put(url(target.getId())).contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("Peter", "Horvath", other.getEmail().toUpperCase(Locale.ROOT), null)),
                UserRole.ADMIN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void teacherWithoutSchool_returns400WithFieldError() throws Exception {
        School school = saveSchool(true);
        User teacher = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.TEACHER, school, "Alfa", true);

        as(put(url(teacher.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(updateJson("Jana", "Novakova", teacher.getEmail(), null)), UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.schoolId").exists());
    }

    @Test
    void toInactiveSchool_returns409() throws Exception {
        School inactive = saveSchool(false);
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT);

        as(put(url(target.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(updateJson("Peter", "Horvath", target.getEmail(), inactive.getId())), UserRole.ADMIN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHOOL_INACTIVE"));
    }

    @Test
    void unknownUser_returns404() throws Exception {
        as(put(url(999999999L)).contentType(MediaType.APPLICATION_JSON)
                .content(updateJson("Peter", "Horvath", uniqueEmail(), null)), UserRole.ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void invalidEmail_returns400WithFieldError() throws Exception {
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT);

        as(put(url(target.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(updateJson("Peter", "Horvath", "not-an-email", null)), UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    private static String updateJson(String name, String surname, String email, Long schoolId) {
        return "{\"name\":\"" + name + "\",\"surname\":\"" + surname + "\",\"email\":\"" + email
                + "\",\"schoolId\":" + schoolId + "}";
    }
}