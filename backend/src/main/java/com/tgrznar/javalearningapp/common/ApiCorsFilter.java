package com.tgrznar.javalearningapp.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * Lets a browser page from a configured origin (see {@link CorsProperties}) call the API under /api/.
 * <p>
 * This is a servlet filter that runs before every other filter of the application, not Spring Security's
 * own CORS support. The body size filter and the rate limit filter run before Spring Security and answer
 * with 413 and 429 themselves. If CORS were handled later, those answers would carry no CORS headers, the
 * browser would hide them and the frontend would see only a network error instead of the error code and
 * Retry-After. Because this filter is first, a preflight request is answered here and never reaches the
 * rate limiter, and every later answer (401, 403, 413, 429, 5xx) already carries the headers.
 * <p>
 * With no allowed origin nothing is registered, so the filter changes nothing. Credentials (cookies) are
 * not allowed; a variant that needs them has to switch that on explicitly.
 */
@Slf4j
@Component
@Order(ApiCorsFilter.ORDER)
public class ApiCorsFilter extends CorsFilter {

    /** Before BodySizeLimitFilter (HIGHEST_PRECEDENCE + 5) and RateLimitFilter (HIGHEST_PRECEDENCE + 10). */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 1;

    private static final List<String> METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final List<String> REQUEST_HEADERS = List.of("Authorization", "Content-Type");
    /** Retry-After is not readable by a page unless it is exposed; the client needs it for 429 and 503. */
    private static final List<String> EXPOSED_HEADERS = List.of("Retry-After");

    public ApiCorsFilter(CorsProperties properties) {
        super(sourceFor(properties));
    }

    private static CorsConfigurationSource sourceFor(CorsProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (properties.enabled()) {
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.setAllowedOrigins(properties.allowedOrigins());
            configuration.setAllowedMethods(METHODS);
            configuration.setAllowedHeaders(REQUEST_HEADERS);
            configuration.setExposedHeaders(EXPOSED_HEADERS);
            configuration.setMaxAge(properties.maxAge());
            source.registerCorsConfiguration("/api/**", configuration);
            log.info("CORS is on for /api/** from: {}", properties.allowedOrigins());
        }
        return source;
    }
}
