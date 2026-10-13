package com.tgrznar.javalearningapp.schoolclass.admin.api;

import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminClassCreateApiTest extends AdminClassApiTestSupport {

    @Test
    void create_asAdmin_returns201WithGeneratedJoinCode() throws Exception {
        School school = saveSchool(true);
        User teacher = saveTeacher(school);

        asAdmin(json(post(URL), createBody("3.A", school.getId(), teacher.getId())))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("3.A"))
                .andExpect(jsonPath("$.schoolId").value(school.getId().intValue()))
                .andExpect(jsonPath("$.teacherId").value(teacher.getId().intValue()))
                .andExpect(jsonPath("$.teacherName").value("Jano Novak"))
                .andExpect(jsonPath("$.joinCode", matchesPattern(JOIN_CODE_PATTERN)))
                .andExpect(jsonPath("$.active").value(true));

        assertThat(classRepository.existsBySchoolIdAndNameIgnoreCase(school.getId(), "3.A")).isTrue();
    }

    @Test
    void create_nameWithSurroundingSpaces_isTrimmed() throws Exception {
        School school = saveSchool(true);
        User teacher = saveTeacher(school);

        asAdmin(json(post(URL), createBody("  3.B  ", school.getId(), teacher.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("3.B"));
    }

    @Test
    void create_blankName_returns400() throws Exception {
        School school = saveSchool(true);
        User teacher = saveTeacher(school);

        asAdmin(json(post(URL), createBody("   ", school.getId(), teacher.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.name").value("Názov triedy je povinný"));
    }

    @Test
    void create_tooLongName_returns400() throws Exception {
        School school = saveSchool(true);
        User teacher = saveTeacher(school);

        asAdmin(json(post(URL), createBody("x".repeat(101), school.getId(), teacher.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").value("Názov triedy môže mať najviac 100 znakov"));
    }

    @Test
    void create_missingSchoolAndTeacher_returns400() throws Exception {
        asAdmin(json(post(URL), createBody("3.A", null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.schoolId").value("Škola je povinná"))
                .andExpect(jsonPath("$.fieldErrors.teacherId").value("Učiteľ je povinný"));
    }

    @Test
    void create_unknownSchool_returns404() throws Exception {
        asAdmin(json(post(URL), createBody("3.A", UNKNOWN_ID, UNKNOWN_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCHOOL_NOT_FOUND"));
    }

    @Test
    void create_inactiveSchool_returns409() throws Exception {
        School school = saveSchool(false);
        User teacher = saveTeacher(school);

        asAdmin(json(post(URL), createBody("3.A", school.getId(), teacher.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHOOL_INACTIVE"));
    }

    @Test
    void create_unknownTeacher_returns404() throws Exception {
        School school = saveSchool(true);

        asAdmin(json(post(URL), createBody("3.A", school.getId(), UNKNOWN_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void create_userWhoIsNotTeacher_returns400() throws Exception {
        School school = saveSchool(true);
        User student = saveUser(UserRole.STUDENT, school, true);

        asAdmin(json(post(URL), createBody("3.A", school.getId(), student.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.teacherId").value("Vybraný používateľ nie je učiteľ"));
    }

    @Test
    void create_inactiveTeacher_returns400() throws Exception {
        School school = saveSchool(true);
        User teacher = saveUser(UserRole.TEACHER, school, false);

        asAdmin(json(post(URL), createBody("3.A", school.getId(), teacher.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.teacherId").value("Vybraný učiteľ je neaktívny"));
    }

    @Test
    void create_teacherFromOtherSchool_returns400() throws Exception {
        School school = saveSchool(true);
        School otherSchool = saveSchool(true);
        User teacher = saveTeacher(otherSchool);

        asAdmin(json(post(URL), createBody("3.A", school.getId(), teacher.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.teacherId").value("Vybraný učiteľ patrí do inej školy"));
    }

    @Test
    void create_duplicateNameIgnoringCase_returns409() throws Exception {
        School school = saveSchool(true);
        User teacher = saveTeacher(school);
        saveClass(school, teacher, "3.A", true);

        asAdmin(json(post(URL), createBody("3.a", school.getId(), teacher.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLASS_NAME_ALREADY_EXISTS"));
    }

    @Test
    void create_sameNameInOtherSchool_returns201() throws Exception {
        School school = saveSchool(true);
        School otherSchool = saveSchool(true);
        saveClass(school, saveTeacher(school), "3.A", true);
        User otherTeacher = saveTeacher(otherSchool);

        asAdmin(json(post(URL), createBody("3.A", otherSchool.getId(), otherTeacher.getId())))
                .andExpect(status().isCreated());
    }

    @Test
    void create_onlyAdminAllowed() throws Exception {
        assertOnlyAdminAllowed(() -> json(post(URL), createBody("3.A", 1L, 2L)));
    }
}