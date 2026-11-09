package com.tgrznar.javalearningapp.user.teacher.api;

import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.schoolclass.SchoolClass;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/v1/teacher/students (UC-11): the module matrix of the students of the teacher's own classes.
 * Runs against a real MySQL, because the scope of the teacher is a database query.
 */
class TeacherStudentOverviewApiTest extends TeacherStudentApiTestSupport {

    // ---------- access control ----------

    @Test
    void overview_onlyATeacherMayCallIt() throws Exception {
        assertOnlyTeacherAllowed(() -> get(URL));
    }

    @Test
    void overview_teacherWithoutSchool_isDenied() throws Exception {
        User noSchool = testUsers.user(UserRole.TEACHER).school(null).save();

        asUser(get(URL), noSchool)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void overview_deactivatedTeacherWithAStillValidToken_isDenied() throws Exception {
        String token = jwtService.generateAccessToken(teacher);
        teacher.setActive(false);
        userRepository.saveAndFlush(teacher);

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void overview_teacherWhoBecameAStudentWithAStillValidToken_isDenied() throws Exception {
        String token = jwtService.generateAccessToken(teacher);
        teacher.setRole(UserRole.STUDENT);
        userRepository.saveAndFlush(teacher);

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    // ---------- scope ----------

    @Test
    void overview_showsOnlyStudentsOfTheTeachersOwnClasses() throws Exception {
        User mine = saveStudent(ownClass);
        User alsoMine = saveStudent(ownClass);
        saveStudent(saveForeignClassInSameSchool());
        saveStudent(saveClassInOtherSchool());

        String body = asUser(get(URL), teacher)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students.totalElements").value(2))
                .andReturn().getResponse().getContentAsString();

        assertThat(longs(body, "$.students.content[*].studentId"))
                .containsExactlyInAnyOrder(mine.getId(), alsoMine.getId());
    }

    @Test
    void overview_aTeacherOfTheSameSchoolWithoutAClassSeesNobody() throws Exception {
        saveStudent(ownClass);
        User colleague = saveTeacher(school);

        asUser(get(URL), colleague)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students.content.length()").value(0))
                .andExpect(jsonPath("$.students.totalElements").value(0))
                .andExpect(jsonPath("$.students.totalPages").value(0));
    }

    @Test
    void overview_doesNotListTeachersOrAdminsEvenWhenTheyAreInTheClass() throws Exception {
        User student = saveStudent(ownClass);
        testUsers.user(UserRole.TEACHER).inClass(ownClass).save();
        testUsers.user(UserRole.ADMIN).inClass(ownClass).save();

        String body = asUser(get(URL), teacher)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students.totalElements").value(1))
                .andReturn().getResponse().getContentAsString();

        assertThat(longs(body, "$.students.content[*].studentId")).containsExactly(student.getId());
    }

    @Test
    void overview_teacherMovedToAnotherSchoolSeesNothingFromHisOldClass() throws Exception {
        saveStudent(ownClass);
        teacher.setSchool(saveSchool());
        userRepository.saveAndFlush(teacher);

        asUser(get(URL), teacher)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students.totalElements").value(0));
        asUser(get(URL).param("classId", String.valueOf(ownClass.getId())), teacher)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLASS_NOT_FOUND"));
    }

    @Test
    void overview_showsDeactivatedStudentsAndStudentsOfDeactivatedClasses() throws Exception {
        User inactive = saveStudent(ownClass, false);
        SchoolClass closedClass = saveClass(school, teacher, "Closed", false);
        User inClosedClass = saveStudent(closedClass);

        String body = asUser(get(URL), teacher)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students.totalElements").value(2))
                .andReturn().getResponse().getContentAsString();

        List<Long> ids = longs(body, "$.students.content[*].studentId");
        assertThat(ids).containsExactlyInAnyOrder(inactive.getId(), inClosedClass.getId());
        int inactiveIndex = ids.indexOf(inactive.getId());
        int closedIndex = ids.indexOf(inClosedClass.getId());
        asUser(get(URL), teacher)
                .andExpect(jsonPath("$.students.content[" + inactiveIndex + "].active").value(false))
                .andExpect(jsonPath("$.students.content[" + inactiveIndex + "].classActive").value(true))
                .andExpect(jsonPath("$.students.content[" + closedIndex + "].active").value(true))
                .andExpect(jsonPath("$.students.content[" + closedIndex + "].classActive").value(false))
                .andExpect(jsonPath("$.students.content[" + closedIndex + "].className").value("Closed"));
    }

    // ---------- filters ----------

    @Test
    void overview_classFilter_returnsOnlyStudentsOfThatOwnClass() throws Exception {
        SchoolClass second = saveClass(school, teacher, "1.B", true);
        saveStudent(ownClass);
        User inSecond = saveStudent(second);

        String body = asUser(get(URL).param("classId", String.valueOf(second.getId())), teacher)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students.totalElements").value(1))
                .andReturn().getResponse().getContentAsString();

        assertThat(longs(body, "$.students.content[*].studentId")).containsExactly(inSecond.getId());
    }

    @Test
    void overview_classFilter_foreignOrUnknownClassIsNotFound() throws Exception {
        List<Long> classIds = List.of(
                saveForeignClassInSameSchool().getId(),
                saveClassInOtherSchool().getId(),
                UNKNOWN_ID);

        for (Long classId : classIds) {
            asUser(get(URL).param("classId", String.valueOf(classId)), teacher)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CLASS_NOT_FOUND"));
        }
    }

    @Test
    void overview_activeFilter_splitsActiveAndDeactivatedStudents() throws Exception {
        User active = saveStudent(ownClass, true);
        User inactive = saveStudent(ownClass, false);

        String activeBody = asUser(get(URL).param("active", "true"), teacher)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String inactiveBody = asUser(get(URL).param("active", "false"), teacher)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(longs(activeBody, "$.students.content[*].studentId")).containsExactly(active.getId());
        assertThat(longs(inactiveBody, "$.students.content[*].studentId")).containsExactly(inactive.getId());
    }

    // ---------- matrix ----------

    @Test
    void overview_buildsTheMatrixOfActiveModulesInCourseOrder() throws Exception {
        CourseModule second = saveModule("Second", 2, true);
        CourseModule first = saveModule("First", 1, true);
        saveModule("Hidden", 3, false);
        User done = saveStudent(ownClass, "Jan", "Adamovic");
        User fresh = saveStudent(ownClass, "Jana", "Bednarova");
        saveProgress(done, first, true, true, true);
        saveProgress(done, second, true, false, false);

        asUser(get(URL), teacher)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules.length()").value(2))
                .andExpect(jsonPath("$.modules[0].moduleId").value(first.getId()))
                .andExpect(jsonPath("$.modules[0].title").value("First"))
                .andExpect(jsonPath("$.modules[0].orderNumber").value(1))
                .andExpect(jsonPath("$.modules[1].moduleId").value(second.getId()))
                // the student who did something
                .andExpect(jsonPath("$.students.content[0].studentId").value(done.getId()))
                .andExpect(jsonPath("$.students.content[0].name").value("Jan"))
                .andExpect(jsonPath("$.students.content[0].surname").value("Adamovic"))
                .andExpect(jsonPath("$.students.content[0].email").value(done.getEmail()))
                .andExpect(jsonPath("$.students.content[0].active").value(true))
                .andExpect(jsonPath("$.students.content[0].classId").value(ownClass.getId()))
                .andExpect(jsonPath("$.students.content[0].className").value("1.A"))
                .andExpect(jsonPath("$.students.content[0].classActive").value(true))
                .andExpect(jsonPath("$.students.content[0].completedModules").value(1))
                .andExpect(jsonPath("$.students.content[0].modules.length()").value(2))
                .andExpect(jsonPath("$.students.content[0].modules[0].moduleId").value(first.getId()))
                .andExpect(jsonPath("$.students.content[0].modules[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.students.content[0].modules[0].theoryCompleted").value(true))
                .andExpect(jsonPath("$.students.content[0].modules[0].quizCompleted").value(true))
                .andExpect(jsonPath("$.students.content[0].modules[0].codingCompleted").value(true))
                .andExpect(jsonPath("$.students.content[0].modules[1].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.students.content[0].modules[1].theoryCompleted").value(true))
                .andExpect(jsonPath("$.students.content[0].modules[1].quizCompleted").value(false))
                // the student who did nothing
                .andExpect(jsonPath("$.students.content[1].studentId").value(fresh.getId()))
                .andExpect(jsonPath("$.students.content[1].completedModules").value(0))
                .andExpect(jsonPath("$.students.content[1].modules[0].status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.students.content[1].modules[1].status").value("NOT_STARTED"));
    }

    @Test
    void overview_progressOfAnotherStudentDoesNotLeakIntoTheRow() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        User other = saveStudent(saveForeignClassInSameSchool());
        saveProgress(other, module, true, true, true);
        User mine = saveStudent(ownClass);

        asUser(get(URL), teacher)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students.content[0].studentId").value(mine.getId()))
                .andExpect(jsonPath("$.students.content[0].completedModules").value(0))
                .andExpect(jsonPath("$.students.content[0].modules[0].status").value("NOT_STARTED"));
    }

    @Test
    void overview_withoutAnyModule_hasEmptyMatrixButStillListsTheStudents() throws Exception {
        saveStudent(ownClass);

        asUser(get(URL), teacher)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules.length()").value(0))
                .andExpect(jsonPath("$.students.content[0].modules.length()").value(0))
                .andExpect(jsonPath("$.students.content[0].completedModules").value(0));
    }

    // ---------- order and paging ----------

    @Test
    void overview_sortsByClassThenSurnameThenName_andPages() throws Exception {
        SchoolClass second = saveClass(school, teacher, "1.B", true);
        User novakZita = saveStudent(ownClass, "Zita", "Novak");
        User novakAdam = saveStudent(ownClass, "Adam", "Novak");
        User kovac = saveStudent(ownClass, "Peter", "Kovac");
        User aaa = saveStudent(second, "Beata", "Aaa");

        String firstPage = asUser(get(URL).param("size", "2"), teacher)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students.page").value(0))
                .andExpect(jsonPath("$.students.size").value(2))
                .andExpect(jsonPath("$.students.totalElements").value(4))
                .andExpect(jsonPath("$.students.totalPages").value(2))
                .andReturn().getResponse().getContentAsString();
        String secondPage = asUser(get(URL).param("size", "2").param("page", "1"), teacher)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students.page").value(1))
                .andReturn().getResponse().getContentAsString();

        assertThat(longs(firstPage, "$.students.content[*].studentId"))
                .containsExactly(kovac.getId(), novakAdam.getId());
        assertThat(longs(secondPage, "$.students.content[*].studentId"))
                .containsExactly(novakZita.getId(), aaa.getId());
    }

    @Test
    void overview_correctsOutOfRangePageAndSize() throws Exception {
        saveStudent(ownClass);

        asUser(get(URL).param("page", "-4").param("size", "100000"), teacher)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students.page").value(0))
                .andExpect(jsonPath("$.students.size").value(100));
        asUser(get(URL).param("size", "0"), teacher)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students.size").value(20));
    }

    // ---------- what must not leak ----------

    @Test
    void overview_neverReturnsJoinCodeHashOrCodeFields() throws Exception {
        saveStudent(ownClass);
        saveModule("First", 1, true);

        String body = asUser(get(URL), teacher)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(ownClass.getJoinCode(), "not-a-real-hash");
        assertThat(body).doesNotContain("joinCode", "passwordHash", "password",
                "submittedCode", "actualOutput", "errorMessage");
    }

    // ---------- number of queries ----------

    @Test
    void overview_numberOfQueriesDoesNotGrowWithTheNumberOfStudents() throws Exception {
        CourseModule first = saveModule("First", 1, true);
        CourseModule second = saveModule("Second", 2, true);
        User firstStudent = saveStudent(ownClass);
        saveProgress(firstStudent, first, true, false, false);
        saveStudent(ownClass);

        Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            long fewStudents = queriesOfOverview(statistics);

            for (int i = 0; i < 13; i++) {
                User student = saveStudent(ownClass);
                saveProgress(student, first, true, true, false);
                saveProgress(student, second, true, false, false);
            }
            long manyStudents = queriesOfOverview(statistics);

            assertThat(fewStudents).isGreaterThan(0);
            // 2 students and 15 students both fit into one page of 20, so even the count query is skipped
            // the same way in both runs.
            assertThat(manyStudents).isEqualTo(fewStudents);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    private long queriesOfOverview(Statistics statistics) throws Exception {
        flushAndClear();
        statistics.clear();
        asUser(get(URL), teacher).andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }
}
