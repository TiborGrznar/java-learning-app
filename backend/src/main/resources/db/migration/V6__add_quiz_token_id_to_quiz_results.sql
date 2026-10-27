-- A quiz token may be used for one submission only. The id of the token ("jti") is stored with the result,
-- and the unique key makes MySQL reject a second submission with the same token, even when two requests
-- arrive at the same moment. Rows written before this migration have no token id (NULL), and MySQL allows
-- any number of NULLs in a unique index. The column needs no foreign key: it belongs to the student's own
-- row, so it is removed together with the student (quiz_results.user_id is ON DELETE CASCADE since V5).

ALTER TABLE quiz_results
    ADD COLUMN quiz_token_id CHAR(36) NULL,
    ADD CONSTRAINT uq_quiz_results_quiz_token_id UNIQUE (quiz_token_id);