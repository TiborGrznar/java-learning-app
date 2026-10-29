package com.tgrznar.javalearningapp.runner.run;

import com.tgrznar.javalearningapp.runner.security.RequestBodyTooLargeException;
import com.tgrznar.javalearningapp.runner.sandbox.exception.SandboxBusyException;
import com.tgrznar.javalearningapp.runner.sandbox.exception.SandboxUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Maps failures of POST /run to a small, fixed JSON body: {"error": "CODE"}. The body never
 * contains the request, a stack trace or the reason a sandbox failed (that goes to the log).
 *
 * <p>The backend relies on the status: 503 means "try again later" (busy), 500 means the sandbox
 * itself is broken, and 400 and 413 mean the backend sent something the runner refuses.
 */
@RestControllerAdvice
public class RunExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RunExceptionHandler.class);

    /** Seconds the backend is advised to wait before it retries a busy runner. */
    static final String RETRY_AFTER_SECONDS = "5";

    /** Response body of every error. */
    public record ErrorBody(String error) {
    }

    @ExceptionHandler(SandboxBusyException.class)
    ResponseEntity<ErrorBody> busy() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .body(new ErrorBody("SANDBOX_BUSY"));
    }

    @ExceptionHandler(SandboxUnavailableException.class)
    ResponseEntity<ErrorBody> unavailable(SandboxUnavailableException e) {
        log.error("Sandbox failure: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorBody("SANDBOX_UNAVAILABLE"));
    }

    /** Input over the sandbox limits, or a request the sandbox refuses to build. */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorBody> invalidInput() {
        return ResponseEntity.badRequest().body(new ErrorBody("INVALID_REQUEST"));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class})
    ResponseEntity<ErrorBody> invalidBody() {
        return ResponseEntity.badRequest().body(new ErrorBody("INVALID_REQUEST"));
    }

    /** Malformed JSON, or a body that grew past the limit while it was being read (chunked requests). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorBody> unreadable(HttpMessageNotReadableException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof RequestBodyTooLargeException) {
                return tooLarge();
            }
        }
        return ResponseEntity.badRequest().body(new ErrorBody("INVALID_REQUEST"));
    }

    @ExceptionHandler(RequestBodyTooLargeException.class)
    ResponseEntity<ErrorBody> tooLarge() {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(new ErrorBody("PAYLOAD_TOO_LARGE"));
    }
}
