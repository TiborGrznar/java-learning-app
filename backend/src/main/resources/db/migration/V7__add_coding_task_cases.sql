-- Coding tasks are checked by running the student's code against several cases
-- (input on stdin, expected output compared by the backend) instead of one
-- expected output per task. This migration:
--   * moves the expected output from coding_tasks to the new coding_task_cases table,
--   * adds the fields an administrator needs to manage a task (order, driver code,
--     reference solution, active flag),
--   * extends coding_results with the number of passed cases.
--
-- coding_tasks has no rows in production yet (no endpoint creates them), so dropping
-- expected_output loses nothing.

ALTER TABLE coding_tasks
    DROP COLUMN expected_output,
    ADD COLUMN order_number       INT UNSIGNED NOT NULL DEFAULT 1 AFTER module_id,
    ADD COLUMN driver_code        LONGTEXT     NULL,
    ADD COLUMN reference_solution LONGTEXT     NULL,
    ADD COLUMN is_active          BOOLEAN      NOT NULL DEFAULT FALSE;

-- The default only served the (empty) existing rows, new rows must set the order explicitly.
ALTER TABLE coding_tasks ALTER COLUMN order_number DROP DEFAULT;

-- A case belongs to its task and has no meaning without it, so it is removed with the task.
-- A task that already has results can never be deleted (coding_results.coding_task_id is RESTRICT),
-- so this cascade cannot remove cases that students' results refer to.
CREATE TABLE coding_task_cases (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    coding_task_id  BIGINT UNSIGNED NOT NULL,
    order_number    INT UNSIGNED    NOT NULL,
    input_text      LONGTEXT        NOT NULL,
    expected_output LONGTEXT        NOT NULL,
    is_hidden       BOOLEAN         NOT NULL DEFAULT TRUE,

    CONSTRAINT uq_coding_task_cases_task_order UNIQUE (coding_task_id, order_number),
    CONSTRAINT fk_coding_task_cases_task FOREIGN KEY (coding_task_id) REFERENCES coding_tasks (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

ALTER TABLE coding_results
    ADD COLUMN passed_cases INT UNSIGNED NULL,
    ADD COLUMN total_cases  INT UNSIGNED NULL;

-- "Has the student passed this task" looks at user, task and status. The new index starts with the
-- same two columns as the old one, so it replaces it. It is created first because the foreign key
-- on user_id needs an index to rely on while the old one is dropped.
CREATE INDEX idx_coding_results_user_task_status ON coding_results (user_id, coding_task_id, status);
DROP INDEX idx_coding_results_user_task ON coding_results;
