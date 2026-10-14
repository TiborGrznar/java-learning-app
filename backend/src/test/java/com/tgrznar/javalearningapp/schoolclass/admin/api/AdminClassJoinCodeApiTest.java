package com.tgrznar.javalearningapp.schoolclass.admin.api;

import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.schoolclass.SchoolClass;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminClassJoinCodeApiTest extends AdminClassApiTestSupport {

    @Test
    void regenerate_replacesCodeAndOldOneStopsExisting() throws Exception {
        School school = saveSchool(true);
        SchoolClass schoolClass = saveClass(school, saveTeacher(school), "3.A", true);
        String oldCode = schoolClass.getJoinCode();

        asAdmin(post(joinCodeUrl(schoolClass.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.joinCode", matchesPattern(JOIN_CODE_PATTERN)))
                .andExpect(jsonPath("$.joinCode", not(equalTo(oldCode))));

        assertThat(classRepository.existsByJoinCode(oldCode)).isFalse();
    }

    @Test
    void regenerate_inactiveClass_works() throws Exception {
        School school = saveSchool(true);
        SchoolClass schoolClass = saveClass(school, saveTeacher(school), "3.A", false);

        asAdmin(post(joinCodeUrl(schoolClass.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.joinCode", matchesPattern(JOIN_CODE_PATTERN)));
    }

    @Test
    void regenerate_unknownClass_returns404() throws Exception {
        asAdmin(post(joinCodeUrl(UNKNOWN_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLASS_NOT_FOUND"));
    }

    @Test
    void regenerate_onlyAdminAllowed() throws Exception {
        assertOnlyAdminAllowed(() -> post(joinCodeUrl(1L)));
    }
}