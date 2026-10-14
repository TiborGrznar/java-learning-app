-- Classes within a school. A class is led by one teacher and students join it with a join code.
-- Existing students keep class_id = NULL until they enroll.
-- All foreign keys use ON DELETE RESTRICT, same as the initial schema; classes are deactivated, not deleted.

CREATE TABLE classes (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    school_id  BIGINT UNSIGNED NOT NULL,
    teacher_id BIGINT UNSIGNED NOT NULL,
    name       VARCHAR(100)    NOT NULL,
    join_code  VARCHAR(16)     NOT NULL,
    is_active  BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_classes_join_code UNIQUE (join_code),
    CONSTRAINT uq_classes_school_name UNIQUE (school_id, name),
    CONSTRAINT fk_classes_school FOREIGN KEY (school_id) REFERENCES schools (id) ON DELETE RESTRICT,
    CONSTRAINT fk_classes_teacher FOREIGN KEY (teacher_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

ALTER TABLE users
    ADD COLUMN class_id BIGINT UNSIGNED NULL AFTER school_id,
    ADD CONSTRAINT fk_users_class FOREIGN KEY (class_id) REFERENCES classes (id) ON DELETE RESTRICT;