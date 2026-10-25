package com.tgrznar.javalearningapp.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain unit tests of the catch-all handler, no Spring context needed. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unexpectedException_returns500WithoutLeakingDetails() throws Exception {
        ResponseEntity<ErrorResponse> response =
                handler.handleUnexpected(new IllegalStateException("select * from users: secret detail"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).doesNotContain("secret detail").doesNotContain("select");
    }

    @Test
    void accessDenied_isRethrownSoTheSecurityFilterChainAnswers() {
        AccessDeniedException ex = new AccessDeniedException("denied");

        assertThatThrownBy(() -> handler.handleUnexpected(ex)).isSameAs(ex);
    }

    @Test
    void authenticationException_isRethrownSoTheSecurityFilterChainAnswers() {
        BadCredentialsException ex = new BadCredentialsException("bad");

        assertThatThrownBy(() -> handler.handleUnexpected(ex)).isSameAs(ex);
    }

    @Test
    void frameworkException_keepsItsStatusAndHeaders() throws Exception {
        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(
                new HttpRequestMethodNotSupportedException("GET", List.of("POST")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getHeaders().getFirst("Allow")).isEqualTo("POST");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    void typeMismatch_returns400() {
        ErrorResponse body = handler.handleTypeMismatch();

        assertThat(body.code()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void typeMismatchException_isHandledByTheDedicatedHandlerNotTheCatchAll() throws NoSuchMethodException {
        // Spring picks the closest exception type, so a TypeMismatchException must never become a 500.
        var method = GlobalExceptionHandler.class.getMethod("handleTypeMismatch");
        var annotation = method.getAnnotation(org.springframework.web.bind.annotation.ExceptionHandler.class);

        assertThat(annotation.value()).containsExactly(TypeMismatchException.class);
    }
}