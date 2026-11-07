package com.tgrznar.javalearningapp.common;

import com.tgrznar.javalearningapp.coderunner.CodeRunnerBusyException;
import com.tgrznar.javalearningapp.coderunner.CodeRunnerUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** The answers for a busy or broken code runner. Plain unit tests, no Spring context. */
class GlobalExceptionHandlerCodeRunnerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void busyRunner_returns503WithRetryAfter() {
        ResponseEntity<ErrorResponse> response = handler.handleCodeRunnerBusy(new CodeRunnerBusyException(7));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("7");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("CODE_RUNNER_BUSY");
    }

    @Test
    void unavailableRunner_returnsAGenericMessageWithoutDetails() {
        ErrorResponse body = handler.handleCodeRunnerUnavailable(
                new CodeRunnerUnavailableException("The code runner answered HTTP 401"));

        assertThat(body.code()).isEqualTo("CODE_RUNNER_UNAVAILABLE");
        assertThat(body.message()).doesNotContain("401").doesNotContain("HTTP");
    }
}
