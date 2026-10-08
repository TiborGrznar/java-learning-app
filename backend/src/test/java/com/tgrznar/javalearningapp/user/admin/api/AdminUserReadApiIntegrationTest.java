package com.tgrznar.javalearningapp.user.admin.api;

import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/v1/users and GET /api/v1/users/{id}: list with filters and paging, and detail. */
class AdminUserReadApiIntegrationTest extends AdminUserApiTestSupport {

    // ---------- access control ----------

    @Test
    void list_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void list_asStudent_returns403() throws Exception {
        as(get(URL), UserRole.STUDENT)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void list_asTeacher_returns403() throws Exception {
        as(get(URL), UserRole.TEACHER)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void get_asTeacher_returns403() throws Exception {
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT);

        as(get(url(target.getId())), UserRole.TEACHER)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    // ---------- list ----------

    @Test
    void list_returnsPageShapeWithoutPasswordHash() throws Exception {
        as(get(URL).param("size", "1"), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber())
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
    }

    @Test
    void list_filtersBySchool() throws Exception {
        School school = saveSchoolWithThreeUsers();

        as(get(URL).param("schoolId", school.getId().toString()), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].schoolName").value(school.getName()));
    }

    @Test
    void list_filtersByRole() throws Exception {
        School school = saveSchoolWithThreeUsers();

        as(get(URL).param("schoolId", school.getId().toString()).param("role", "TEACHER"), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].surname").value("Beta"));
    }

    @Test
    void list_filtersByActiveFlag() throws Exception {
        School school = saveSchoolWithThreeUsers();

        as(get(URL).param("schoolId", school.getId().toString()).param("active", "false"), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].surname").value("Gama"));
    }

    @Test
    void list_isSortedBySurname() throws Exception {
        School school = saveSchoolWithThreeUsers();

        as(get(URL).param("schoolId", school.getId().toString()), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].surname").value("Alfa"))
                .andExpect(jsonPath("$.content[1].surname").value("Beta"))
                .andExpect(jsonPath("$.content[2].surname").value("Gama"));
    }

    @Test
    void list_paginates() throws Exception {
        School school = saveSchoolWithThreeUsers();

        as(get(URL).param("schoolId", school.getId().toString()).param("size", "2"), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2));

        as(get(URL).param("schoolId", school.getId().toString()).param("size", "2").param("page", "1"),
                UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].surname").value("Gama"));
    }

    @Test
    void list_tooLargeSize_isCappedAtMaximum() throws Exception {
        as(get(URL).param("size", "1000"), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void list_negativePage_isTreatedAsFirstPage() throws Exception {
        as(get(URL).param("page", "-1"), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void list_unknownRoleFilter_returns400() throws Exception {
        // Known gap: the response is Spring's default error body, not our JSON (see open-decisions.md).
        as(get(URL).param("role", "SUPERHERO"), UserRole.ADMIN)
                .andExpect(status().isBadRequest());
    }

    // ---------- detail ----------

    @Test
    void get_returnsUser() throws Exception {
        School school = saveSchool(true);
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.TEACHER, school, "Alfa", true);

        as(get(url(target.getId())), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(target.getId()))
                .andExpect(jsonPath("$.email").value(target.getEmail()))
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andExpect(jsonPath("$.schoolId").value(school.getId()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void get_unknownUser_returns404() throws Exception {
        as(get(url(999999999L)), UserRole.ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    /** A school with a teacher (Beta), an active student (Alfa) and an inactive student (Gama). */
    private School saveSchoolWithThreeUsers() {
        School school = saveSchool(true);
        saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.TEACHER, school, "Beta", true);
        saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT, school, "Alfa", true);
        saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT, school, "Gama", false);
        return school;
    }
}