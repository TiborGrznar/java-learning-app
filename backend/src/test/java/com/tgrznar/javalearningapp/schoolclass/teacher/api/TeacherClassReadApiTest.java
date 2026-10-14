package com.tgrznar.javalearningapp.schoolclass.teacher.api;

import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.schoolclass.SchoolClass;
import com.tgrznar.javalearningapp.user.model.User;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeacherClassReadApiTest extends TeacherClassApiTestSupport {

    // ---------- list ----------

    @Test
    void list_returnsOnlyOwnClassesOrderedByName() throws Exception {
        School school = saveSchool();
        User owner = saveTeacher(school);
        User other = saveTeacher(school);
        saveClass(school, owner, "2.B", true);
        saveClass(school, owner, "1.A", true);
        saveClass(school, other, "3.C", true);

        asUser(get(URL), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", equalTo("1.A")))
                .andExpect(jsonPath("$[1].name", equalTo("2.B")))
                .andExpect(jsonPath("$[0].teacherId", equalTo(owner.getId().intValue())))
                .andExpect(jsonPath("$[1].teacherId", equalTo(owner.getId().intValue())));
    }

    @Test
    void list_filtersByActiveFlag() throws Exception {
        School school = saveSchool();
        User owner = saveTeacher(school);
        saveClass(school, owner, "1.A", true);
        saveClass(school, owner, "2.B", false);

        asUser(get(URL).param("active", "true"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", equalTo("1.A")));

        asUser(get(URL).param("active", "false"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", equalTo("2.B")));

        asUser(get(URL), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void list_teacherWithoutClasses_returnsEmptyArray() throws Exception {
        School school = saveSchool();
        User owner = saveTeacher(school);
        User other = saveTeacher(school);
        saveClass(school, other, "1.A", true);

        asUser(get(URL), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void list_onlyTeacherAllowed() throws Exception {
        assertOnlyTeacherAllowed(() -> get(URL));
    }

    // ---------- get ----------

    @Test
    void get_ownClass_returnsDetailWithJoinCode() throws Exception {
        School school = saveSchool();
        User owner = saveTeacher(school);
        SchoolClass own = saveClass(school, owner, "1.A", true);

        asUser(get(url(own.getId())), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(own.getId().intValue())))
                .andExpect(jsonPath("$.name", equalTo("1.A")))
                .andExpect(jsonPath("$.joinCode", equalTo(own.getJoinCode())))
                .andExpect(jsonPath("$.teacherId", equalTo(owner.getId().intValue())))
                .andExpect(jsonPath("$.active", equalTo(true)));
    }

    @Test
    void get_ownInactiveClass_isReturned() throws Exception {
        School school = saveSchool();
        User owner = saveTeacher(school);
        SchoolClass own = saveClass(school, owner, "1.A", false);

        asUser(get(url(own.getId())), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", equalTo(false)));
    }

    @Test
    void get_classOfAnotherTeacher_returnsNotFoundWithoutLeakingJoinCode() throws Exception {
        School school = saveSchool();
        User owner = saveTeacher(school);
        User intruder = saveTeacher(school);
        SchoolClass own = saveClass(school, owner, "1.A", true);

        asUser(get(url(own.getId())), intruder)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("CLASS_NOT_FOUND")))
                .andExpect(jsonPath("$.joinCode").doesNotExist());
    }

    @Test
    void get_unknownClass_returnsNotFound() throws Exception {
        User teacher = saveTeacher(saveSchool());

        asUser(get(url(UNKNOWN_ID)), teacher)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("CLASS_NOT_FOUND")));
    }

    @Test
    void get_onlyTeacherAllowed() throws Exception {
        School school = saveSchool();
        SchoolClass own = saveClass(school, saveTeacher(school), "1.A", true);

        assertOnlyTeacherAllowed(() -> get(url(own.getId())));
    }
}