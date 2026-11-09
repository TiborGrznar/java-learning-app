package com.tgrznar.javalearningapp.user.teacher.api;

import com.tgrznar.javalearningapp.auth.token.RefreshToken;
import com.tgrznar.javalearningapp.auth.token.RefreshTokenRepository;
import com.tgrznar.javalearningapp.schoolclass.SchoolClass;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PATCH /api/v1/teacher/students/active (UC-12): bulk deactivation and reactivation of the students of the
 * teacher's own classes. The request is all or nothing: one id outside the teacher's scope answers 404 and
 * changes nobody.
 */
class TeacherStudentActiveApiTest extends TeacherStudentApiTestSupport {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    // ---------- access control ----------

    @Test
    void setActive_onlyATeacherMayCallIt() throws Exception {
        User student = saveStudent(ownClass);

        assertOnlyTeacherAllowed(() -> request(List.of(student.getId()), false));

        flushAndClear();
        assertThat(reload(student).isActive()).isTrue();
    }

    @Test
    void setActive_teacherWithoutSchool_isDeniedAndChangesNothing() throws Exception {
        User student = saveStudent(ownClass);
        User noSchool = testUsers.user(UserRole.TEACHER).school(null).save();

        asUser(request(List.of(student.getId()), false), noSchool)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        flushAndClear();
        assertThat(reload(student).isActive()).isTrue();
    }

    @Test
    void setActive_deactivatedTeacherWithAStillValidToken_isDeniedAndChangesNothing() throws Exception {
        User student = saveStudent(ownClass);
        String token = jwtService.generateAccessToken(teacher);
        teacher.setActive(false);
        userRepository.saveAndFlush(teacher);

        mockMvc.perform(request(List.of(student.getId()), false).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        flushAndClear();
        assertThat(reload(student).isActive()).isTrue();
    }

    // ---------- the change ----------

    @Test
    void setActive_deactivatesOwnStudents() throws Exception {
        User first = saveStudent(ownClass);
        User second = saveStudent(ownClass);
        User untouched = saveStudent(ownClass);

        String body = asUser(request(List.of(first.getId(), second.getId()), false), teacher)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andReturn().getResponse().getContentAsString();

        assertThat(longs(body, "$.changed")).containsExactlyInAnyOrder(first.getId(), second.getId());
        assertThat(longs(body, "$.unchanged")).isEmpty();
        flushAndClear();
        assertThat(reload(first).isActive()).isFalse();
        assertThat(reload(second).isActive()).isFalse();
        assertThat(reload(untouched).isActive()).isTrue();
    }

    @Test
    void setActive_reactivatesOwnStudents() throws Exception {
        User first = saveStudent(ownClass, false);
        User second = saveStudent(ownClass, false);

        String body = asUser(request(List.of(first.getId(), second.getId()), true), teacher)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(longs(body, "$.changed")).containsExactlyInAnyOrder(first.getId(), second.getId());
        flushAndClear();
        assertThat(reload(first).isActive()).isTrue();
        assertThat(reload(second).isActive()).isTrue();
    }

    @Test
    void setActive_studentAlreadyInTheRequestedState_isReportedAsUnchangedNotAsAnError() throws Exception {
        User active = saveStudent(ownClass, true);
        User inactive = saveStudent(ownClass, false);

        String deactivation = asUser(request(List.of(active.getId(), inactive.getId()), false), teacher)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(longs(deactivation, "$.changed")).containsExactly(active.getId());
        assertThat(longs(deactivation, "$.unchanged")).containsExactly(inactive.getId());

        String reactivation = asUser(request(List.of(active.getId(), inactive.getId()), true), teacher)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(longs(reactivation, "$.changed")).containsExactlyInAnyOrder(active.getId(), inactive.getId());
        assertThat(longs(reactivation, "$.unchanged")).isEmpty();
    }

    @Test
    void setActive_repeatedIdCountsOnce() throws Exception {
        User first = saveStudent(ownClass);
        User second = saveStudent(ownClass);

        String body = asUser(request(List.of(first.getId(), second.getId(), first.getId()), false), teacher)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(longs(body, "$.changed")).containsExactlyInAnyOrder(first.getId(), second.getId());
        assertThat(longs(body, "$.unchanged")).isEmpty();
    }

    @Test
    void setActive_studentsOfADeactivatedClassCanBeChanged() throws Exception {
        SchoolClass closedClass = saveClass(school, teacher, "Closed", false);
        User student = saveStudent(closedClass);

        asUser(request(List.of(student.getId()), false), teacher)
                .andExpect(status().isOk());

        flushAndClear();
        assertThat(reload(student).isActive()).isFalse();
    }

    @Test
    void setActive_studentsOfAllOwnClassesCanBeChangedTogether() throws Exception {
        SchoolClass second = saveClass(school, teacher, "1.B", true);
        User inFirst = saveStudent(ownClass);
        User inSecond = saveStudent(second);

        asUser(request(List.of(inFirst.getId(), inSecond.getId()), false), teacher)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed.length()").value(2));
    }

    // ---------- refresh tokens ----------

    @Test
    void setActive_deactivationRevokesRefreshTokensOfChangedStudentsOnly_andReactivationDoesNotRestoreThem()
            throws Exception {
        User changed = saveStudent(ownClass);
        User notInRequest = saveStudent(ownClass);
        RefreshToken changedToken = saveToken(changed);
        RefreshToken otherToken = saveToken(notInRequest);
        RefreshToken teacherToken = saveToken(teacher);

        asUser(request(List.of(changed.getId()), false), teacher).andExpect(status().isOk());

        flushAndClear();
        assertThat(isRevoked(changedToken)).isTrue();
        assertThat(isRevoked(otherToken)).isFalse();
        assertThat(isRevoked(teacherToken)).isFalse();

        asUser(request(List.of(changed.getId()), true), teacher).andExpect(status().isOk());

        flushAndClear();
        assertThat(reload(changed).isActive()).isTrue();
        assertThat(isRevoked(changedToken)).isTrue();
    }

    // ---------- all or nothing ----------

    @Test
    void setActive_oneForeignStudentAmongOwnOnes_answers404AndChangesNobody() throws Exception {
        User own = saveStudent(ownClass);
        User foreign = saveStudent(saveForeignClassInSameSchool());
        RefreshToken ownToken = saveToken(own);

        asUser(request(List.of(own.getId(), foreign.getId()), false), teacher)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

        flushAndClear();
        assertThat(reload(own).isActive()).isTrue();
        assertThat(reload(foreign).isActive()).isTrue();
        assertThat(isRevoked(ownToken)).isFalse();
    }

    @Test
    void setActive_unknownIdAmongOwnOnes_answers404AndChangesNobody() throws Exception {
        User own = saveStudent(ownClass);

        asUser(request(List.of(own.getId(), UNKNOWN_ID), false), teacher)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

        flushAndClear();
        assertThat(reload(own).isActive()).isTrue();
    }

    @Test
    void setActive_everyKindOfAccountOutsideTheScopeAnswersTheSame404() throws Exception {
        User own = saveStudent(ownClass);
        User otherSchoolStudent = saveStudent(saveClassInOtherSchool());
        User admin = testUsers.user(UserRole.ADMIN).school(school).save();
        User colleague = saveTeacher(school);
        // a student of the same school who is in no class at all
        User withoutClass = testUsers.user(UserRole.STUDENT).school(school).save();
        List<Long> outside = List.of(otherSchoolStudent.getId(), admin.getId(), colleague.getId(),
                teacher.getId(), withoutClass.getId());

        for (Long id : outside) {
            asUser(request(List.of(own.getId(), id), false), teacher)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        }

        flushAndClear();
        assertThat(reload(own).isActive()).isTrue();
        assertThat(reload(teacher).isActive()).isTrue();
        assertThat(reload(admin).isActive()).isTrue();
        assertThat(reload(colleague).isActive()).isTrue();
    }

    @Test
    void setActive_teacherMovedToAnotherSchoolCannotChangeStudentsOfHisOldClass() throws Exception {
        User student = saveStudent(ownClass);
        teacher.setSchool(saveSchool());
        userRepository.saveAndFlush(teacher);

        asUser(request(List.of(student.getId()), false), teacher)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

        flushAndClear();
        assertThat(reload(student).isActive()).isTrue();
    }

    // ---------- validation ----------

    @Test
    void setActive_emptyOrMissingList_returns400() throws Exception {
        asUser(raw("{\"studentIds\":[],\"active\":false}"), teacher)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.studentIds").exists());
        asUser(raw("{\"active\":false}"), teacher)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.studentIds").exists());
    }

    @Test
    void setActive_moreThan100Students_returns400() throws Exception {
        List<Long> ids = LongStream.rangeClosed(1, 101).boxed().toList();

        asUser(request(ids, false), teacher)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.studentIds").exists());
    }

    @Test
    void setActive_exactly100Students_passesValidation() throws Exception {
        // none of these ids is a student of this teacher, so the answer is the 404 of the service, not a 400
        List<Long> ids = LongStream.rangeClosed(UNKNOWN_ID, UNKNOWN_ID + 99).boxed().toList();

        asUser(request(ids, false), teacher)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void setActive_nullIdInTheList_returns400() throws Exception {
        asUser(raw("{\"studentIds\":[null],\"active\":false}"), teacher)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void setActive_missingActiveFlag_returns400AndChangesNothing() throws Exception {
        User student = saveStudent(ownClass);

        asUser(raw("{\"studentIds\":[" + student.getId() + "]}"), teacher)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.active").exists());

        flushAndClear();
        assertThat(reload(student).isActive()).isTrue();
    }

    // ---------- helpers ----------

    private static MockHttpServletRequestBuilder request(List<Long> studentIds, boolean active) {
        String ids = studentIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        return raw("{\"studentIds\":[" + ids + "],\"active\":" + active + "}");
    }

    private static MockHttpServletRequestBuilder raw(String json) {
        return patch(activeUrl()).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private RefreshToken saveToken(User user) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        // 64 characters like the SHA-256 hex the column holds
        token.setTokenHash(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
        token.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        token.setRevoked(false);
        return refreshTokenRepository.saveAndFlush(token);
    }

    /** Reads the token from the database (the bulk revoke bypasses the persistence context, so callers clear first). */
    private boolean isRevoked(RefreshToken token) {
        return refreshTokenRepository.findById(token.getId()).orElseThrow().isRevoked();
    }
}
