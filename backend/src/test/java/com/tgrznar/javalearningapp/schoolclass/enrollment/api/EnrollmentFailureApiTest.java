package com.tgrznar.javalearningapp.schoolclass.enrollment.api;

import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.schoolclass.SchoolClass;
import com.tgrznar.javalearningapp.user.model.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EnrollmentFailureApiTest extends EnrollmentApiTestSupport {

    @Test
    void enroll_unknownCode_returnsNotFoundAndLeavesTheStudentAlone() throws Exception {
        User student = saveStudent(null);

        enroll(student, UNKNOWN_CODE)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("INVALID_JOIN_CODE")))
                .andExpect(jsonPath("$.message", equalTo(INVALID_CODE_MESSAGE)));

        assertThat(userRepository.findById(student.getId()).orElseThrow().getSchoolClass()).isNull();
    }

    @Test
    void enroll_inactiveClass_isAnsweredExactlyLikeAnUnknownCode() throws Exception {
        School school = saveSchool(true);
        SchoolClass inactive = saveClass(school, saveTeacher(school), "1.A", false);
        User student = saveStudent(null);

        enroll(student, inactive.getJoinCode())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("INVALID_JOIN_CODE")))
                .andExpect(jsonPath("$.message", equalTo(INVALID_CODE_MESSAGE)));

        assertThat(userRepository.findById(student.getId()).orElseThrow().getSchoolClass()).isNull();
    }

    @Test
    void enroll_activeClassOfAnInactiveSchool_isAnsweredExactlyLikeAnUnknownCode() throws Exception {
        School inactiveSchool = saveSchool(false);
        SchoolClass schoolClass = saveClass(inactiveSchool, saveTeacher(inactiveSchool), "1.A", true);
        User student = saveStudent(null);

        enroll(student, schoolClass.getJoinCode())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("INVALID_JOIN_CODE")))
                .andExpect(jsonPath("$.message", equalTo(INVALID_CODE_MESSAGE)));
    }

    @Test
    void enroll_alreadyEnrolledStudent_returnsConflictAndKeepsTheFirstClass() throws Exception {
        School school = saveSchool(true);
        User teacher = saveTeacher(school);
        SchoolClass first = saveClass(school, teacher, "1.A", true);
        SchoolClass second = saveClass(school, teacher, "1.B", true);
        User student = saveStudent(null);
        enroll(student, first.getJoinCode()).andExpect(status().isOk());

        enroll(student, second.getJoinCode())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("ALREADY_ENROLLED")));

        assertThat(userRepository.findById(student.getId()).orElseThrow().getSchoolClass().getId())
                .isEqualTo(first.getId());
    }

    @Test
    void enroll_alreadyEnrolledStudent_neverGetsBlocked() throws Exception {
        School school = saveSchool(true);
        SchoolClass schoolClass = saveClass(school, saveTeacher(school), "1.A", true);
        User student = saveStudent(null);
        enroll(student, schoolClass.getJoinCode()).andExpect(status().isOk());

        // Turning away an enrolled student is not a failed attempt, so the limit is never reached.
        for (int i = 0; i < 8; i++) {
            enroll(student, UNKNOWN_CODE)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code", equalTo("ALREADY_ENROLLED")));
        }
    }

    @Test
    void enroll_studentOfAnotherSchool_returnsConflictAndChangesNothing() throws Exception {
        School classSchool = saveSchool(true);
        SchoolClass schoolClass = saveClass(classSchool, saveTeacher(classSchool), "1.A", true);
        School otherSchool = saveSchool(true);
        User student = saveStudent(otherSchool);

        enroll(student, schoolClass.getJoinCode())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("SCHOOL_MISMATCH")));

        User stored = userRepository.findById(student.getId()).orElseThrow();
        assertThat(stored.getSchoolClass()).isNull();
        assertThat(stored.getSchool().getId()).isEqualTo(otherSchool.getId());
    }

    @Test
    void enroll_blankCode_returnsValidationError() throws Exception {
        User student = saveStudent(null);

        enroll(student, "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_ERROR")));
        enroll(student, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_ERROR")));
    }

    @Test
    void enroll_onlyStudentAllowed() throws Exception {
        assertOnlyStudentAllowed(() -> json(post(URL), enrollBody(UNKNOWN_CODE)));
    }
}