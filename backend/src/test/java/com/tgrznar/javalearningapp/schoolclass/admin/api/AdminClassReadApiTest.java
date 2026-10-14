package com.tgrznar.javalearningapp.schoolclass.admin.api;

import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.schoolclass.SchoolClass;
import com.tgrznar.javalearningapp.user.model.User;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminClassReadApiTest extends AdminClassApiTestSupport {

    @Test
    void list_filteredBySchool_ordersByNameAndShowsJoinCode() throws Exception {
        School school = saveSchool(true);
        School otherSchool = saveSchool(true);
        User teacher = saveTeacher(school);
        SchoolClass second = saveClass(school, teacher, "B-class", true);
        saveClass(school, teacher, "A-class", true);
        saveClass(otherSchool, saveTeacher(otherSchool), "C-class", true);

        asAdmin(get(URL).param("schoolId", school.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("A-class"))
                .andExpect(jsonPath("$[1].name").value("B-class"))
                .andExpect(jsonPath("$[1].joinCode").value(second.getJoinCode()))
                .andExpect(jsonPath("$[1].schoolName").value(school.getName()));
    }

    @Test
    void list_filteredByTeacher_returnsOnlyHisClasses() throws Exception {
        School school = saveSchool(true);
        User teacher = saveTeacher(school);
        User otherTeacher = saveTeacher(school);
        saveClass(school, teacher, "A-class", true);
        saveClass(school, otherTeacher, "B-class", true);

        asAdmin(get(URL).param("teacherId", teacher.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("A-class"));
    }

    @Test
    void list_filteredByActive_returnsOnlyMatchingClasses() throws Exception {
        School school = saveSchool(true);
        User teacher = saveTeacher(school);
        saveClass(school, teacher, "Active", true);
        saveClass(school, teacher, "Inactive", false);

        asAdmin(get(URL).param("schoolId", school.getId().toString()).param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Inactive"))
                .andExpect(jsonPath("$[0].active").value(false));
    }

    @Test
    void get_returnsDetail() throws Exception {
        School school = saveSchool(true);
        User teacher = saveTeacher(school);
        SchoolClass schoolClass = saveClass(school, teacher, "3.A", true);

        asAdmin(get(url(schoolClass.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(schoolClass.getId().intValue()))
                .andExpect(jsonPath("$.name").value("3.A"))
                .andExpect(jsonPath("$.schoolId").value(school.getId().intValue()))
                .andExpect(jsonPath("$.teacherId").value(teacher.getId().intValue()))
                .andExpect(jsonPath("$.teacherName").value("Jano Novak"))
                .andExpect(jsonPath("$.joinCode", matchesPattern("[A-F0-9]{8}")))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void get_unknownClass_returns404() throws Exception {
        asAdmin(get(url(UNKNOWN_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLASS_NOT_FOUND"));
    }

    @Test
    void list_onlyAdminAllowed() throws Exception {
        assertOnlyAdminAllowed(() -> get(URL));
    }

    @Test
    void get_onlyAdminAllowed() throws Exception {
        assertOnlyAdminAllowed(() -> get(url(1L)));
    }
}