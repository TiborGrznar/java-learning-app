package com.tgrznar.javalearningapp.ratelimit;

import com.tgrznar.javalearningapp.common.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Per-IP limits, applied before authentication so a flood of requests is stopped early.
 * Everything under /api/v1/ is limited: the three public auth endpoints have their own rule,
 * every other request falls under the general one, so a new endpoint is never unlimited.
 * Limits that need the request body (failed logins per e-mail) or the user (quiz loads) live in the services.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/v1/";

    private final RateLimiters limiters;
    private final ClientIpResolver ipResolver;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        RateLimiter limiter = select(request);
        if (limiter != null) {
            try {
                limiter.checkAndRecord(ipResolver.resolve(request));
            } catch (RateLimitExceededException e) {
                reject(response, e.getRetryAfterSeconds());
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private RateLimiter select(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith(API_PREFIX)) {
            return null;
        }
        if ("POST".equals(request.getMethod())) {
            switch (path) {
                case "/api/v1/auth/login":
                    return limiters.loginPerIp();
                case "/api/v1/auth/register":
                    return limiters.registerPerIp();
                case "/api/v1/auth/refresh":
                    return limiters.refreshPerIp();
                default:
                    break;
            }
        }
        return limiters.apiPerIp();
    }

    private void reject(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ErrorResponse.of("RATE_LIMITED", "Príliš veľa požiadaviek. Skúste to znova neskôr"));
    }
}