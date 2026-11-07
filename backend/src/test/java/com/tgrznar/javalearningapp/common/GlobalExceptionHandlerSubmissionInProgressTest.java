package com.tgrznar.javalearningapp.common;

import com.tgrznar.javalearningapp.learning.exception.SubmissionInProgressException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The answer for a second submission while the first is still being evaluated. Plain unit test, no Spring context. */
class GlobalExceptionHandlerSubmissionInProgressTest {

    @Test
    void submissionInProgress_hasItsOwnCode() {
        ErrorResponse body = new GlobalExceptionHandler().handleSubmissionInProgress();

        assertThat(body.code()).isEqualTo("SUBMISSION_IN_PROGRESS");
        assertThat(body.message()).isNotBlank();
        assertThat(new SubmissionInProgressException().getMessage()).doesNotContain(body.code());
    }
}
