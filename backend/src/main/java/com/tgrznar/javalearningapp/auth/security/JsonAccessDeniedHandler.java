package com.tgrznar.javalearningapp.auth.security;

import com.tgrznar.javalearningapp.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Answers requests of an authenticated user who lacks the required role (for example a student
 * calling an admin endpoint) with 403 and the uniform JSON error body. Unauthenticated requests
 * never get here, they end in JsonAuthenticationEntryPoint with 401.
 * The required role is deliberately not mentioned in the response.
 */
@Component
@RequiredArgsConstructor
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ErrorResponse.of("ACCESS_DENIED", "Nemáte oprávnenie na túto akciu"));
    }
}