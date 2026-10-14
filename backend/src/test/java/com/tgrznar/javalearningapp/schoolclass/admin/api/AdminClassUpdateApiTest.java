package com.tgrznar.javalearningapp.schoolclass.admin.api;

import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.schoolclass.SchoolClass;
import com.tgrznar.javalearningapp.user.model.User;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminClassUpdateApiTest extends AdminClassApiTestSupport {

    @Test
    void update_renamesAndChangesTeacher_keepingSchoolAndJoinCode() throws Exception {
        School school = saveSchool(true);
        User teacher = saveTeacher(school);
        User newTeacher = saveTeacher(school);
        SchoolClass schoolClass = saveClass(school, teacher, "3.A", true);

        asAdmin(json(put(url(schoolClass.getId())), updateBody("3.B", newTeacher.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("3.B"))
                .andExpect(jsonPath("$.teacherId").value(newTeacher.getId().intValue()))
                .andExpect(jsonPath("$.schoolId").value(school.getId().intValue()))
                .andExpect(jsonPath("$.joinCode").value(schoolClass.getJoinCode()));
    }

    @Test
    void update_nameDifferingOnlyInCaseOfItself_isAllowed() throws Exception {
        School school = saveSchool(true);
        User teacher = saveTeacher(school);
        SchoolClass schoolClass = saveClass(school, teacher, "3.A", true);

        asAdmin(json(put(url(schoolClass.getId())), updateBody("3.a", teacher.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("3.a"));
    }

    @Test
    void update_nameTakenByOtherClass_returns409() throws Exception {
        School school = saveSchool(true);
        User teacher = saveTeacher(school);
        saveClass(school, teacher, "3.A", true);
        SchoolClass other = saveClass(school, teacher, "3.B", true);

        asAdmin(json(put(url(other.getId())), updateBody("3.a", teacher.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLASS_NAME_ALREADY_EXISTS"));
    }

    @Test
    void update_teacherFromOtherSchool_returns400() throws Exception {
        School school = saveSchool(true);
        School otherSchool = saveSchool(true);
        User teacher = saveTeacher(school);
        User foreignTeacher = saveTeacher(otherSchool);
        SchoolClass schoolClass = saveClass(school, teacher, "3.A", true);

        asAdmin(json(put(url(schoolClass.getId())), updateBody("3.A", foreignTeacher.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.teacherId").value("Vybraný učiteľ patrí do inej školy"));
    }

    @Test
    void update_unchangedInactiveTeacher_isAllowed() throws Exception {
        School school = saveSchool(true);
        User teacher = saveTeacher(school);
        SchoolClass schoolClass = saveClass(school, teacher, "3.A", false);
        teacher.setActive(false);
        userRepository.saveAndFlush(teacher);

        asAdmin(json(put(url(schoolClass.getId())), updateBody("3.B", teacher.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("3.B"))
                .andExpect(jsonPath("$.teacherId").value(teacher.getId().intValue()));
    }

    @Test
    void update_unknownClass_returns404() throws Exception {
        asAdmin(json(put(url(UNKNOWN_ID)), updateBody("3.A", 1L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLASS_NOT_FOUND"));
    }

    @Test
    void update_invalidBody_returns400() throws Exception {
        School school = saveSchool(true);
        SchoolClass schoolClass = saveClass(school, saveTeacher(school), "3.A", true);

        asAdmin(json(put(url(schoolClass.getId())), updateBody(" ", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.name").value("Názov triedy je povinný"))
                .andExpect(jsonPath("$.fieldErrors.teacherId").value("Učiteľ je povinný"));
    }

    @Test
    void update_onlyAdminAllowed() throws Exception {
        assertOnlyAdminAllowed(() -> json(put(url(1L)), updateBody("3.A", 2L)));
    }
}