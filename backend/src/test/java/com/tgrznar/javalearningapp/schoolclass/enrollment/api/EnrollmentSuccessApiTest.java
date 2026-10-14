package com.tgrznar.javalearningapp.schoolclass.enrollment.api;

import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.schoolclass.SchoolClass;
import com.tgrznar.javalearningapp.user.model.User;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EnrollmentSuccessApiTest extends EnrollmentApiTestSupport {

    @Test
    void enroll_withValidCode_returnsTheClassAndStoresClassAndSchool() throws Exception {
        School school = saveSchool(true);
        SchoolClass schoolClass = saveClass(school, saveTeacher(school), "1.A", true);
        User student = saveStudent(null);

        enroll(student, schoolClass.getJoinCode())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classId", equalTo(schoolClass.getId().intValue())))
                .andExpect(jsonPath("$.className", equalTo("1.A")))
                .andExpect(jsonPath("$.schoolId", equalTo(school.getId().intValue())))
                .andExpect(jsonPath("$.schoolName", equalTo(school.getName())))
                .andExpect(jsonPath("$.joinCode").doesNotExist());

        User stored = userRepository.findById(student.getId()).orElseThrow();
        assertThat(stored.getSchoolClass().getId()).isEqualTo(schoolClass.getId());
        assertThat(stored.getSchool().getId()).isEqualTo(school.getId());
    }

    @Test
    void enroll_codeWithSpacesAndLowerCase_isAccepted() throws Exception {
        School school = saveSchool(true);
        SchoolClass schoolClass = saveClass(school, saveTeacher(school), "1.A", true);
        User student = saveStudent(null);

        enroll(student, "  " + schoolClass.getJoinCode().toLowerCase(Locale.ROOT) + "  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classId", equalTo(schoolClass.getId().intValue())));
    }

    @Test
    void enroll_studentAlreadyInTheSchoolOfTheClass_isAccepted() throws Exception {
        School school = saveSchool(true);
        SchoolClass schoolClass = saveClass(school, saveTeacher(school), "1.A", true);
        User student = saveStudent(school);

        enroll(student, schoolClass.getJoinCode())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schoolId", equalTo(school.getId().intValue())));
    }

    @Test
    void enroll_afterTheTeacherRegeneratedTheCode_theOldCodeStopsWorking() throws Exception {
        School school = saveSchool(true);
        User teacher = saveTeacher(school);
        SchoolClass schoolClass = saveClass(school, teacher, "1.A", true);
        String oldCode = schoolClass.getJoinCode();
        User student = saveStudent(null);

        asUser(post(teacherJoinCodeUrl(schoolClass.getId())), teacher).andExpect(status().isOk());
        String newCode = classRepository.findById(schoolClass.getId()).orElseThrow().getJoinCode();
        assertThat(newCode).isNotEqualTo(oldCode);

        enroll(student, oldCode)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("INVALID_JOIN_CODE")));
        enroll(student, newCode)
                .andExpect(status().isOk());
    }
}