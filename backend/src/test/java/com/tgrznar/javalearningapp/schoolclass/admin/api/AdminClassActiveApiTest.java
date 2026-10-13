package com.tgrznar.javalearningapp.schoolclass.admin.api;

import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.schoolclass.SchoolClass;
import com.tgrznar.javalearningapp.user.model.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminClassActiveApiTest extends AdminClassApiTestSupport {

    private static String activeUrl(Long id) {
        return url(id) + "/active";
    }

    @Test
    void deactivate_returns200AndStoresFlag() throws Exception {
        School school = saveSchool(true);
        SchoolClass schoolClass = saveClass(school, saveTeacher(school), "3.A", true);

        asAdmin(json(patch(activeUrl(schoolClass.getId())), activeBody(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        assertThat(classRepository.findById(schoolClass.getId()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void activate_validClass_returns200() throws Exception {
        School school = saveSchool(true);
        SchoolClass schoolClass = saveClass(school, saveTeacher(school), "3.A", false);

        asAdmin(json(patch(activeUrl(schoolClass.getId())), activeBody(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void activate_inactiveSchool_returns409() throws Exception {
        School school = saveSchool(true);
        SchoolClass schoolClass = saveClass(school, saveTeacher(school), "3.A", false);
        school.setActive(false);
        schoolRepository.saveAndFlush(school);

        asAdmin(json(patch(activeUrl(schoolClass.getId())), activeBody(true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHOOL_INACTIVE"));
    }

    @Test
    void activate_inactiveTeacher_returns400() throws Exception {
        School school = saveSchool(true);
        User teacher = saveTeacher(school);
        SchoolClass schoolClass = saveClass(school, teacher, "3.A", false);
        teacher.setActive(false);
        userRepository.saveAndFlush(teacher);

        asAdmin(json(patch(activeUrl(schoolClass.getId())), activeBody(true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.teacherId").value("Vybraný učiteľ je neaktívny"));
    }

    @Test
    void setActive_sameValue_isNoOp() throws Exception {
        School school = saveSchool(true);
        SchoolClass schoolClass = saveClass(school, saveTeacher(school), "3.A", true);

        asAdmin(json(patch(activeUrl(schoolClass.getId())), activeBody(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void setActive_missingFlag_returns400() throws Exception {
        School school = saveSchool(true);
        SchoolClass schoolClass = saveClass(school, saveTeacher(school), "3.A", true);

        asAdmin(json(patch(activeUrl(schoolClass.getId())), activeBody(null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.active").value("Stav triedy je povinný"));
    }

    @Test
    void setActive_unknownClass_returns404() throws Exception {
        asAdmin(json(patch(activeUrl(UNKNOWN_ID)), activeBody(true)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLASS_NOT_FOUND"));
    }

    @Test
    void setActive_onlyAdminAllowed() throws Exception {
        assertOnlyAdminAllowed(() -> json(patch(activeUrl(1L)), activeBody(true)));
    }
}