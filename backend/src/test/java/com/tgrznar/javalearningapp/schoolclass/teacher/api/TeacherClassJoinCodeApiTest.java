package com.tgrznar.javalearningapp.schoolclass.teacher.api;

import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.schoolclass.SchoolClass;
import com.tgrznar.javalearningapp.user.model.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeacherClassJoinCodeApiTest extends TeacherClassApiTestSupport {

    @Test
    void regenerate_ownClass_storesNewCodeFromTheAlphabet() throws Exception {
        School school = saveSchool();
        User owner = saveTeacher(school);
        SchoolClass own = saveClass(school, owner, "1.A", true);
        String oldCode = own.getJoinCode();

        asUser(post(joinCodeUrl(own.getId())), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(own.getId().intValue())))
                .andExpect(jsonPath("$.joinCode", matchesPattern(JOIN_CODE_PATTERN)));

        String storedCode = classRepository.findById(own.getId()).orElseThrow().getJoinCode();
        assertThat(storedCode).matches(JOIN_CODE_PATTERN).isNotEqualTo(oldCode);
    }

    @Test
    void regenerate_ownInactiveClass_isAllowed() throws Exception {
        School school = saveSchool();
        User owner = saveTeacher(school);
        SchoolClass own = saveClass(school, owner, "1.A", false);

        asUser(post(joinCodeUrl(own.getId())), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", equalTo(false)))
                .andExpect(jsonPath("$.joinCode", matchesPattern(JOIN_CODE_PATTERN)));
    }

    @Test
    void regenerate_classOfAnotherTeacher_returnsNotFoundAndKeepsCode() throws Exception {
        School school = saveSchool();
        User owner = saveTeacher(school);
        User intruder = saveTeacher(school);
        SchoolClass own = saveClass(school, owner, "1.A", true);
        String oldCode = own.getJoinCode();

        asUser(post(joinCodeUrl(own.getId())), intruder)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("CLASS_NOT_FOUND")));

        assertThat(classRepository.findById(own.getId()).orElseThrow().getJoinCode()).isEqualTo(oldCode);
    }

    @Test
    void regenerate_unknownClass_returnsNotFound() throws Exception {
        User teacher = saveTeacher(saveSchool());

        asUser(post(joinCodeUrl(UNKNOWN_ID)), teacher)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("CLASS_NOT_FOUND")));
    }

    @Test
    void regenerate_onlyTeacherAllowed() throws Exception {
        School school = saveSchool();
        SchoolClass own = saveClass(school, saveTeacher(school), "1.A", true);

        assertOnlyTeacherAllowed(() -> post(joinCodeUrl(own.getId())));
    }
}