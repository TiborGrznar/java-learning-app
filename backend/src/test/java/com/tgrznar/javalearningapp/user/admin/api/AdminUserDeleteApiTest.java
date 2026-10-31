package com.tgrznar.javalearningapp.user.admin.api;

import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.module.CourseModuleRepository;
import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.schoolclass.SchoolClass;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Integration tests of DELETE /api/v1/users/{id}?confirmEmail=... (administrator deletes a student). */
class AdminUserDeleteApiTest extends AdminUserApiTestSupport {

    private static final String[] USER_DATA_TABLES =
            {"user_progress", "quiz_results", "coding_results", "refresh_tokens"};

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CourseModuleRepository moduleRepository;

    // ---------- success ----------

    @Test
    void delete_student_returns204AndRemovesTheUser() throws Exception {
        User student = saveUser(uniqueEmail(), UserRole.STUDENT);

        as(deleteRequest(student), UserRole.ADMIN)
                .andExpect(status().isNoContent());

        assertFalse(userRepository.existsById(student.getId()));
        // The e-mail is free again, so the student can register anew.
        assertFalse(userRepository.existsByEmail(student.getEmail()));
    }

    @Test
    void delete_studentWithLearningData_removesProgressResultsAndTokens() throws Exception {
        User student = saveUser(uniqueEmail(), UserRole.STUDENT);
        insertLearningData(student.getId(), saveModule().getId());
        for (String table : USER_DATA_TABLES) {
            assertEquals(1, countRows(table, student.getId()), table + " before the deletion");
        }

        as(deleteRequest(student), UserRole.ADMIN)
                .andExpect(status().isNoContent());

        for (String table : USER_DATA_TABLES) {
            assertEquals(0, countRows(table, student.getId()), table + " after the deletion");
        }
    }

    @Test
    void delete_studentInClass_keepsTheClass() throws Exception {
        User student = saveStudentInClass().student();

        SchoolClass schoolClass = student.getSchoolClass();
        as(deleteRequest(student), UserRole.ADMIN)
                .andExpect(status().isNoContent());

        assertTrue(classRepository.existsById(schoolClass.getId()));
    }

    @Test
    void delete_confirmEmailIgnoresCase() throws Exception {
        User student = saveUser(uniqueEmail(), UserRole.STUDENT);

        as(delete(url(student.getId())).param("confirmEmail", student.getEmail().toUpperCase(Locale.ROOT)),
                UserRole.ADMIN)
                .andExpect(status().isNoContent());

        assertFalse(userRepository.existsById(student.getId()));
    }

    // ---------- confirmation ----------

    @Test
    void delete_wrongConfirmEmail_returns400AndKeepsTheStudent() throws Exception {
        User student = saveUser(uniqueEmail(), UserRole.STUDENT);

        as(delete(url(student.getId())).param("confirmEmail", "someone-else@example.com"), UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.confirmEmail").exists());

        assertTrue(userRepository.existsById(student.getId()));
    }

    @Test
    void delete_missingConfirmEmail_returns400AndKeepsTheStudent() throws Exception {
        User student = saveUser(uniqueEmail(), UserRole.STUDENT);

        as(delete(url(student.getId())), UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.confirmEmail").exists());

        assertTrue(userRepository.existsById(student.getId()));
    }

    // ---------- who can be deleted ----------

    @Test
    void delete_teacher_returns409UserNotDeletable() throws Exception {
        User teacher = saveUser(uniqueEmail(), UserRole.TEACHER);

        as(deleteRequest(teacher), UserRole.ADMIN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_NOT_DELETABLE"));

        assertTrue(userRepository.existsById(teacher.getId()));
    }

    @Test
    void delete_otherAdmin_returns409UserNotDeletable() throws Exception {
        User otherAdmin = saveUser(uniqueEmail(), UserRole.ADMIN);

        as(deleteRequest(otherAdmin), UserRole.ADMIN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_NOT_DELETABLE"));

        assertTrue(userRepository.existsById(otherAdmin.getId()));
    }

    @Test
    void delete_ownAccountAsAdmin_returns409UserNotDeletable() throws Exception {
        User admin = saveUser(uniqueEmail(), UserRole.ADMIN);

        asUser(deleteRequest(admin), admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_NOT_DELETABLE"));

        assertTrue(userRepository.existsById(admin.getId()));
    }

    @Test
    void delete_unknownUser_returns404() throws Exception {
        as(delete(url(999_999_999L)).param("confirmEmail", "any@example.com"), UserRole.ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    // ---------- access control ----------

    @Test
    void delete_asTeacherOrStudent_returns403AndKeepsTheStudent() throws Exception {
        User student = saveUser(uniqueEmail(), UserRole.STUDENT);

        for (UserRole role : new UserRole[]{UserRole.TEACHER, UserRole.STUDENT}) {
            as(deleteRequest(student), role)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        assertTrue(userRepository.existsById(student.getId()));
    }

    @Test
    void delete_withoutToken_returns401() throws Exception {
        User student = saveUser(uniqueEmail(), UserRole.STUDENT);

        mockMvc.perform(deleteRequest(student))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertTrue(userRepository.existsById(student.getId()));
    }

    // ---------- after the deletion ----------

    @Test
    void delete_deletedStudentsToken_noLongerOpensTheLearningContent() throws Exception {
        User student = saveStudentInClass().student();

        as(deleteRequest(student), UserRole.ADMIN)
                .andExpect(status().isNoContent());

        // The access token itself is stateless and still valid, but the class is read from the database.
        asUser(get("/api/v1/learning/modules"), student)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ENROLLED"));
    }

    // ---------- helpers ----------

    private static MockHttpServletRequestBuilder deleteRequest(User target) {
        return delete(url(target.getId())).param("confirmEmail", target.getEmail());
    }

    private record StudentInClass(User student, SchoolClass schoolClass) {
    }

    /** A student enrolled in a class of a new school, led by a new teacher. */
    private StudentInClass saveStudentInClass() {
        School school = saveSchool(true);
        User teacher = saveUser(uniqueEmail(), UserRole.TEACHER, school, "Novak", true);
        SchoolClass schoolClass = saveClass(teacher, true);
        User student = saveUser(uniqueEmail(), UserRole.STUDENT, school, "Novak", true);
        student.setSchoolClass(schoolClass);
        return new StudentInClass(userRepository.saveAndFlush(student), schoolClass);
    }

    private CourseModule saveModule() {
        CourseModule module = new CourseModule();
        module.setTitle("Module " + UUID.randomUUID());
        module.setDescription("Description");
        module.setTheoryContent("# Theory");
        module.setOrderNumber(1);
        module.setActive(false);
        return moduleRepository.saveAndFlush(module);
    }

    /** One row in each table that references the user, inserted with SQL so the test needs no entities. */
    private void insertLearningData(Long userId, Long moduleId) {
        jdbcTemplate.update("insert into user_progress (user_id, module_id) values (?, ?)", userId, moduleId);
        jdbcTemplate.update("insert into quiz_results (module_id, user_id, score, max_score) values (?, ?, 1, 2)",
                moduleId, userId);
        jdbcTemplate.update("insert into coding_tasks (module_id, order_number, title) values (?, 1, 'Task')",
                moduleId);
        jdbcTemplate.update("""
                insert into coding_results (coding_task_id, user_id, submitted_code, status)
                select id, ?, 'code', 'passed' from coding_tasks where module_id = ?
                """, userId, moduleId);
        // token_hash is CHAR(64): two UUIDs without dashes.
        String tokenHash = (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
        jdbcTemplate.update("insert into refresh_tokens (user_id, token_hash, expires_at) values (?, ?, ?)",
                userId, tokenHash, Timestamp.from(Instant.now().plus(1, ChronoUnit.DAYS)));
    }

    /** The table name comes from the constant array above, never from input. */
    private int countRows(String table, Long userId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + table + " where user_id = ?",
                Integer.class, userId);
        return count == null ? 0 : count;
    }
}