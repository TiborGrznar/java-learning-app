package com.tgrznar.javalearningapp.schoolclass.enrollment.api;

import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.schoolclass.SchoolClass;
import com.tgrznar.javalearningapp.user.model.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EnrollmentRateLimitApiTest extends EnrollmentApiTestSupport {

    private static final int MAX_FAILED_ATTEMPTS = 5;

    @Test
    void afterFiveFailures_evenAValidCodeIsRefusedWithRetryAfter() throws Exception {
        School school = saveSchool(true);
        SchoolClass schoolClass = saveClass(school, saveTeacher(school), "1.A", true);
        User student = saveStudent(null);

        for (int i = 0; i < MAX_FAILED_ATTEMPTS; i++) {
            enroll(student, UNKNOWN_CODE).andExpect(status().isNotFound());
        }

        enroll(student, schoolClass.getJoinCode())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code", equalTo("TOO_MANY_ATTEMPTS")))
                .andExpect(header().exists("Retry-After"));

        assertThat(userRepository.findById(student.getId()).orElseThrow().getSchoolClass()).isNull();
    }

    @Test
    void failuresOfOneStudent_doNotBlockAnotherStudent() throws Exception {
        School school = saveSchool(true);
        SchoolClass schoolClass = saveClass(school, saveTeacher(school), "1.A", true);
        User blocked = saveStudent(null);
        User other = saveStudent(null);

        for (int i = 0; i < MAX_FAILED_ATTEMPTS; i++) {
            enroll(blocked, UNKNOWN_CODE).andExpect(status().isNotFound());
        }
        enroll(blocked, UNKNOWN_CODE).andExpect(status().isTooManyRequests());

        enroll(other, schoolClass.getJoinCode()).andExpect(status().isOk());
    }

    @Test
    void validCodeOfAnotherSchool_countsAsAFailureToo() throws Exception {
        School classSchool = saveSchool(true);
        SchoolClass schoolClass = saveClass(classSchool, saveTeacher(classSchool), "1.A", true);
        User student = saveStudent(saveSchool(true));

        for (int i = 0; i < MAX_FAILED_ATTEMPTS; i++) {
            enroll(student, schoolClass.getJoinCode())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code", equalTo("SCHOOL_MISMATCH")));
        }

        enroll(student, schoolClass.getJoinCode())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code", equalTo("TOO_MANY_ATTEMPTS")));
    }
}