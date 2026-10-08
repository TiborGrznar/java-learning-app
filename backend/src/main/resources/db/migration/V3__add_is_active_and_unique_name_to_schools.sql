-- Adds soft-delete support and enforces unique school names.
-- Existing rows stay active. Fails if duplicate names already exist.

ALTER TABLE schools
    ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT uq_schools_name UNIQUE (name);