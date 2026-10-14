-- Deleting a student removes his progress, quiz results and coding results together with the account.
-- Exception to the ON DELETE RESTRICT convention of V1: this is the data of the person being erased, and
-- only the administrator's "delete student" operation removes users. All other foreign keys stay RESTRICT.
-- Each constraint is dropped and re-added in two statements, because MySQL does not always accept
-- dropping and adding a constraint of the same name in one ALTER.

ALTER TABLE user_progress DROP FOREIGN KEY fk_user_progress_user;
ALTER TABLE user_progress
    ADD CONSTRAINT fk_user_progress_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE quiz_results DROP FOREIGN KEY fk_quiz_results_user;
ALTER TABLE quiz_results
    ADD CONSTRAINT fk_quiz_results_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE coding_results DROP FOREIGN KEY fk_coding_results_user;
ALTER TABLE coding_results
    ADD CONSTRAINT fk_coding_results_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
