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
            "schools", "users", "modules", "quiz_questions", "coding_tasks",
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
     * Student data is deleted with the account (V5), every other foreign key stays RESTRICT.
     */
    @ParameterizedTest
    @CsvSource({
            "fk_user_progress_user, CASCADE",
            "fk_quiz_results_user, CASCADE",
            "fk_coding_results_user, CASCADE",
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
}