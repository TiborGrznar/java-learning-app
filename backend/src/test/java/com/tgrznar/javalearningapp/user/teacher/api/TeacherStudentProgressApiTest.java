package com.tgrznar.javalearningapp.user.teacher.api;

import com.jayway.jsonpath.JsonPath;
import com.tgrznar.javalearningapp.codingresult.CodingResult;
import com.tgrznar.javalearningapp.codingresult.CodingResultRepository;
import com.tgrznar.javalearningapp.codingresult.CodingResultStatus;
import com.tgrznar.javalearningapp.codingtask.CodingTask;
import com.tgrznar.javalearningapp.codingtask.CodingTaskRepository;
import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.quizresult.QuizResult;
import com.tgrznar.javalearningapp.quizresult.QuizResultRepository;
import com.tgrznar.javalearningapp.schoolclass.SchoolClass;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/v1/teacher/students/{studentId}/progress (UC-11 detail). A student outside the teacher's scope, an
 * unknown id and the id of a teacher or an administrator must be indistinguishable: all of them answer the same 404.
 */
class TeacherStudentProgressApiTest extends TeacherStudentApiTestSupport {

    private static final String STUDENT_OWN_VIEW = "/api/v1/learning/progress";

    @Autowired
    private CodingTaskRepository taskRepository;

    @Autowired
    private CodingResultRepository codingResultRepository;

    @Autowired
    private QuizResultRepository quizResultRepository;

    // ---------- access control ----------

    @Test
    void progress_onlyATeacherMayCallIt() throws Exception {
        assertOnlyTeacherAllowed(() -> get(progressUrl(saveStudent(ownClass).getId())));
    }

    @Test
    void progress_teacherWithoutSchool_isDenied() throws Exception {
        User student = saveStudent(ownClass);
        User noSchool = testUsers.user(UserRole.TEACHER).school(null).save();

        asUser(get(progressUrl(student.getId())), noSchool)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    // ---------- own students ----------

    @Test
    void progress_ownStudent_returnsSummaryAndProgress() throws Exception {
        CourseModule first = saveModule("First", 1, true);
        saveModule("Second", 2, true);
        User student = saveStudent(ownClass, "Jan", "Adamovic");
        saveProgress(student, first, true, true, false);

        asUser(get(progressUrl(student.getId())), teacher)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.student.studentId").value(student.getId()))
                .andExpect(jsonPath("$.student.name").value("Jan"))
                .andExpect(jsonPath("$.student.surname").value("Adamovic"))
                .andExpect(jsonPath("$.student.email").value(student.getEmail()))
                .andExpect(jsonPath("$.student.active").value(true))
                .andExpect(jsonPath("$.student.classId").value(ownClass.getId()))
                .andExpect(jsonPath("$.student.className").value("1.A"))
                .andExpect(jsonPath("$.student.classActive").value(true))
                .andExpect(jsonPath("$.progress.completedModules").value(0))
                .andExpect(jsonPath("$.progress.totalModules").value(2))
                .andExpect(jsonPath("$.progress.modules[0].moduleId").value(first.getId()))
                .andExpect(jsonPath("$.progress.modules[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.progress.modules[0].quizCompleted").value(true));
    }

    @Test
    void progress_isExactlyWhatTheStudentSeesInHisOwnOverview() throws Exception {
        CourseModule first = saveModule("First", 1, true);
        CourseModule second = saveModule("Second", 2, true);
        CodingTask task = saveTask(first, 1);
        saveTask(first, 2);
        User student = saveStudent(ownClass);
        saveProgress(student, first, true, true, false);
        saveProgress(student, second, true, false, false);
        saveQuizResult(student, first, 7, 10);
        saveCodingResult(student, task, CodingResultStatus.FAILED, "code");

        String teacherBody = asUser(get(progressUrl(student.getId())), teacher)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String studentBody = asUser(get(STUDENT_OWN_VIEW), student)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> seenByTeacher = JsonPath.read(teacherBody, "$.progress");
        Map<String, Object> seenByStudent = JsonPath.read(studentBody, "$");
        assertThat(seenByTeacher).isEqualTo(seenByStudent);
        // the comparison is not empty by accident
        assertThat((List<?>) seenByTeacher.get("modules")).hasSize(2);
    }

    @Test
    void progress_showsDeactivatedStudentAndStudentOfDeactivatedClass() throws Exception {
        User inactive = saveStudent(ownClass, false);
        SchoolClass closedClass = saveClass(school, teacher, "Closed", false);
        User inClosedClass = saveStudent(closedClass);

        asUser(get(progressUrl(inactive.getId())), teacher)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.student.active").value(false))
                .andExpect(jsonPath("$.student.classActive").value(true));
        asUser(get(progressUrl(inClosedClass.getId())), teacher)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.student.active").value(true))
                .andExpect(jsonPath("$.student.classActive").value(false))
                .andExpect(jsonPath("$.student.className").value("Closed"));
    }

    // ---------- outside the scope ----------

    @Test
    void progress_everythingOutsideTheScopeAnswersTheSame404() throws Exception {
        User sameSchoolForeign = saveStudent(saveForeignClassInSameSchool());
        User otherSchool = saveStudent(saveClassInOtherSchool());
        User admin = testUsers.user(UserRole.ADMIN).school(school).save();
        User colleague = saveTeacher(school);
        List<Long> ids = List.of(sameSchoolForeign.getId(), otherSchool.getId(), admin.getId(),
                colleague.getId(), teacher.getId(), UNKNOWN_ID);

        Set<String> answers = new LinkedHashSet<>();
        for (Long id : ids) {
            String body = asUser(get(progressUrl(id)), teacher)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                    .andReturn().getResponse().getContentAsString();
            answers.add(JsonPath.read(body, "$.code") + "|" + JsonPath.read(body, "$.message"));
        }

        assertThat(answers).hasSize(1);
    }

    @Test
    void progress_teacherMovedToAnotherSchoolCannotSeeStudentOfHisOldClass() throws Exception {
        User student = saveStudent(ownClass);
        teacher.setSchool(saveSchool());
        userRepository.saveAndFlush(teacher);

        asUser(get(progressUrl(student.getId())), teacher)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    // ---------- what must not leak ----------

    @Test
    void progress_neverReturnsCodeOutputErrorsOrTaskInternals() throws Exception {
        CourseModule module = saveModule("First", 1, true);
        CodingTask task = saveTask(module, 1);
        User student = saveStudent(ownClass);
        saveProgress(student, module, true, false, false);
        CodingResult result = new CodingResult();
        result.setUser(student);
        result.setCodingTask(task);
        result.setSubmittedCode("SECRET-CODE");
        result.setStatus(CodingResultStatus.FAILED);
        result.setActualOutput("SECRET-OUTPUT");
        result.setErrorMessage("SECRET-ERROR");
        codingResultRepository.saveAndFlush(result);

        String body = asUser(get(progressUrl(student.getId())), teacher)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("SECRET", ownClass.getJoinCode(), "not-a-real-hash");
        assertThat(body).doesNotContain("submittedCode", "actualOutput", "errorMessage", "joinCode",
                "passwordHash", "driverCode", "referenceSolution", "templateCode", "expectedOutput");
    }

    // ---------- helpers ----------

    private CodingTask saveTask(CourseModule module, int order) {
        CodingTask task = new CodingTask();
        task.setModule(module);
        task.setOrderNumber(order);
        task.setTitle("Task " + order);
        task.setDescription("Description");
        task.setTemplateCode("SECRET-TEMPLATE");
        task.setDriverCode("SECRET-DRIVER");
        task.setReferenceSolution("SECRET-REFERENCE");
        task.setActive(true);
        return taskRepository.saveAndFlush(task);
    }

    private void saveQuizResult(User user, CourseModule module, int score, int maxScore) {
        QuizResult result = new QuizResult();
        result.setUser(user);
        result.setModule(module);
        result.setScore(score);
        result.setMaxScore(maxScore);
        quizResultRepository.saveAndFlush(result);
    }

    private void saveCodingResult(User student, CodingTask task, CodingResultStatus status, String code) {
        CodingResult result = new CodingResult();
        result.setUser(student);
        result.setCodingTask(task);
        result.setSubmittedCode(code);
        result.setStatus(status);
        codingResultRepository.saveAndFlush(result);
    }
}
