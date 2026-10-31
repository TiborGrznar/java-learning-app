package com.tgrznar.javalearningapp.db;

import com.tgrznar.javalearningapp.testsupport.TestcontainersConfiguration;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the Flyway migrations against the empty MySQL database that Testcontainers starts for
 * the test run, so a broken or skipped migration shows up here and not only on a fresh deployment.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FlywayMigrationIntegrationTest {

    private static final List<String> EXPECTED_TABLES = List.of(
            "schools", "users", "modules", "quiz_questions", "coding_tasks", "coding_task_cases",
            "quiz_results", "user_progress", "coding_results", "refresh_tokens", "classes");

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allMigrationsAreApplied() throws IOException {
        int migrationFiles = new PathMatchingResourcePatternResolver()
                .getResources("classpath:db/migration/V*__*.sql").length;

        MigrationInfo[] applied = flyway.info().applied();

        assertThat(applied).hasSize(migrationFiles);
        assertThat(applied).allSatisfy(migration ->
                assertThat(migration.getState()).isEqualTo(MigrationState.SUCCESS));
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void schemaContainsAllTables() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'",
                String.class);

        assertThat(tables).containsAll(EXPECTED_TABLES);
    }

    /**
     * Student data is deleted with the account (V5) and the cases of a task with the task (V7),
     * every other foreign key stays RESTRICT. A task that has results can therefore never be deleted.
     */
    @ParameterizedTest
    @CsvSource({
            "fk_user_progress_user, CASCADE",
            "fk_quiz_results_user, CASCADE",
            "fk_coding_results_user, CASCADE",
            "fk_coding_task_cases_task, CASCADE",
            "fk_coding_results_task, RESTRICT",
            "fk_classes_school, RESTRICT",
            "fk_classes_teacher, RESTRICT",
            "fk_users_class, RESTRICT"
    })
    void foreignKeyHasExpectedDeleteRule(String constraint, String expectedRule) {
        String rule = jdbcTemplate.queryForObject(
                "SELECT delete_rule FROM information_schema.referential_constraints "
                        + "WHERE constraint_schema = DATABASE() AND constraint_name = ?",
                String.class, constraint);

        assertThat(rule).isEqualTo(expectedRule);
    }

    /** V7 moves the expected output into the cases and adds the fields an administrator manages. */
    @Test
    void codingTasksHaveTheV7Columns() {
        assertThat(columnsOf("coding_tasks"))
                .contains("order_number", "driver_code", "reference_solution", "is_active")
                .doesNotContain("expected_output");
        assertThat(columnsOf("coding_task_cases"))
                .contains("coding_task_id", "order_number", "input_text", "expected_output", "is_hidden");
    }

    @Test
    void codingResultsHaveTheV7ColumnsAndIndex() {
        assertThat(columnsOf("coding_results")).contains("passed_cases", "total_cases");

        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT DISTINCT index_name FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'coding_results'",
                String.class);

        assertThat(indexes)
                .contains("idx_coding_results_user_task_status")
                .doesNotContain("idx_coding_results_user_task");
    }

    @Test
    void caseOrderIsUniquePerTask() {
        List<String> constraints = jdbcTemplate.queryForList(
                "SELECT constraint_name FROM information_schema.table_constraints "
                        + "WHERE table_schema = DATABASE() AND table_name = 'coding_task_cases' "
                        + "AND constraint_type = 'UNIQUE'",
                String.class);

        assertThat(constraints).contains("uq_coding_task_cases_task_order");
    }

    private List<String> columnsOf(String table) {
        return jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = ?",
                String.class, table);
    }
}