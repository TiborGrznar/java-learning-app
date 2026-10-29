package com.tgrznar.javalearningapp.runner.security;

import com.tgrznar.javalearningapp.runner.RunnerProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Lets a request through only when it carries the shared secret in the X-Runner-Token header.
 * Everything is protected except GET /health, so a new endpoint is closed by default.
 * <p>
 * The runner also listens on localhost only; the token is a second lock, so another program on
 * the same machine cannot start containers just by finding the port.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RunnerTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Runner-Token";

    private static final String PUBLIC_PATH = "/health";

    private final byte[] expectedToken;

    public RunnerTokenFilter(RunnerProperties properties) {
        this.expectedToken = properties.token().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "GET".equals(request.getMethod()) && PUBLIC_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        // MessageDigest.isEqual compares in constant time, so the response time does not leak the token.
        if (provided != null && MessageDigest.isEqual(expectedToken, provided.getBytes(StandardCharsets.UTF_8))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"UNAUTHORIZED\"}");
    }
}