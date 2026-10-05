-- Refresh tokens for JWT authentication.
-- Only a SHA-256 hash of the token is stored, never the raw value.
--
-- Deletion strategy: this table is an intentional exception to the
-- ON DELETE RESTRICT convention used in V1. Refresh tokens are transient
-- session data, not audit data, so they are removed together with their user.

CREATE TABLE refresh_tokens (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT UNSIGNED NOT NULL,
    token_hash CHAR(64)        NOT NULL,
    expires_at TIMESTAMP       NOT NULL,
    revoked    BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;