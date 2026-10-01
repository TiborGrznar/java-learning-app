-- Initial database schema for the Java Learning App.
-- Defines schools, users, learning modules, quizzes, coding tasks,
-- and the tables that track user progress and results.
--
-- Deletion strategy: all foreign keys use ON DELETE RESTRICT.
-- Parent records that have dependent data (modules, users, schools)
-- are never hard-deleted in production - they are soft-deleted via
-- the is_active flag instead. RESTRICT guards against accidental
-- cascading data loss at the database level.

CREATE TABLE schools (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    address    VARCHAR(255) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE users (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100)  NOT NULL,
    surname       VARCHAR(100)  NOT NULL,
    email         VARCHAR(255)  NOT NULL,
    password_hash VARCHAR(255)  NOT NULL,
    role          ENUM ('student', 'teacher', 'admin') NOT NULL,
    school_id     BIGINT UNSIGNED NULL,
    is_active     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT fk_users_school FOREIGN KEY (school_id) REFERENCES schools (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE modules (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    title          VARCHAR(255)  NOT NULL,
    description    VARCHAR(1000) NULL,
    theory_content LONGTEXT      NULL,
    order_number   INT UNSIGNED  NOT NULL,
    is_active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE quiz_questions (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    module_id      BIGINT UNSIGNED NOT NULL,
    question_text  VARCHAR(1000) NOT NULL,
    option_a       VARCHAR(500)  NOT NULL,
    option_b       VARCHAR(500)  NOT NULL,
    option_c       VARCHAR(500)  NOT NULL,
    option_d       VARCHAR(500)  NOT NULL,
    correct_option ENUM ('A', 'B', 'C', 'D') NOT NULL,

    CONSTRAINT fk_quiz_questions_module FOREIGN KEY (module_id) REFERENCES modules (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE coding_tasks (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    module_id       BIGINT UNSIGNED NOT NULL,
    title           VARCHAR(255)  NOT NULL,
    description     VARCHAR(2000) NULL,
    expected_output LONGTEXT      NOT NULL,
    template_code   LONGTEXT      NULL,

    CONSTRAINT fk_coding_tasks_module FOREIGN KEY (module_id) REFERENCES modules (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE quiz_results (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    module_id    BIGINT UNSIGNED NOT NULL,
    user_id      BIGINT UNSIGNED NOT NULL,
    score        INT UNSIGNED NOT NULL,
    max_score    INT UNSIGNED NOT NULL,
    completed_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_quiz_results_module FOREIGN KEY (module_id) REFERENCES modules (id) ON DELETE RESTRICT,
    CONSTRAINT fk_quiz_results_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_quiz_results_user_module ON quiz_results (user_id, module_id);

CREATE TABLE user_progress (
    id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT UNSIGNED NOT NULL,
    module_id        BIGINT UNSIGNED NOT NULL,
    theory_completed BOOLEAN   NOT NULL DEFAULT FALSE,
    quiz_completed   BOOLEAN   NOT NULL DEFAULT FALSE,
    coding_completed BOOLEAN   NOT NULL DEFAULT FALSE,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_user_progress_user_module UNIQUE (user_id, module_id),
    CONSTRAINT fk_user_progress_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_progress_module FOREIGN KEY (module_id) REFERENCES modules (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE coding_results (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    coding_task_id BIGINT UNSIGNED NOT NULL,
    user_id        BIGINT UNSIGNED NOT NULL,
    submitted_code LONGTEXT NOT NULL,
    status         ENUM ('passed', 'failed', 'compile_error', 'runtime_error', 'timeout') NOT NULL,
    actual_output  LONGTEXT NULL,
    error_message  LONGTEXT NULL,
    executed_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_coding_results_task FOREIGN KEY (coding_task_id) REFERENCES coding_tasks (id) ON DELETE RESTRICT,
    CONSTRAINT fk_coding_results_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_coding_results_user_task ON coding_results (user_id, coding_task_id);