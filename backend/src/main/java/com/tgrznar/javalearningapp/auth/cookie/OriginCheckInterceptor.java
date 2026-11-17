package com.tgrznar.javalearningapp.auth.cookie;

import com.tgrznar.javalearningapp.auth.exception.OriginNotAllowedException;
import com.tgrznar.javalearningapp.common.CorsProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * CSRF defence of the endpoints that work with the refresh token cookie (login, refresh, logout).
 * <p>
 * SameSite=Strict alone is not enough: it judges the site, not the origin, so a page on another port of the
 * same host or on a sibling subdomain is "same site". And with CORS switched off nothing else stops a
 * cross-origin POST from reaching the controller (the browser only hides the answer). A browser sends the
 * Origin header with every POST, and a page cannot change it, so this check closes both gaps.
 * <p>
 * Rule: an Origin that is present must be one of the trusted origins (app.auth.cookie.allowed-origins plus
 * app.cors.allowed-origins), otherwise 403 ORIGIN_NOT_ALLOWED. Origin: null is refused too. A missing Origin
 * passes: it means a client that is not a browser page (Postman, curl, tests), and a browser does not leave
 * it out of a POST. With no trusted origin configured every request that carries an Origin is refused
 * (fail closed), so a same-origin setup must list the public origin of the frontend.
 * <p>
 * Registered in WebMvcConfig, after the rate limit filter. The password change is not covered: it is
 * authenticated by the Authorization header, which a foreign page cannot add.
 */
@Slf4j
@Component
public class OriginCheckInterceptor implements HandlerInterceptor {

    private static final int MAX_LOGGED_ORIGIN_LENGTH = 200;

    private final Set<String> trustedOrigins;

    public OriginCheckInterceptor(CorsProperties corsProperties, RefreshCookieProperties cookieProperties) {
        Set<String> origins = new LinkedHashSet<>(corsProperties.allowedOrigins());
        origins.addAll(cookieProperties.allowedOrigins());
        this.trustedOrigins = Set.copyOf(origins);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null || isTrusted(origin)) {
            return true;
        }
        // The value comes from the client, so it is cut before it goes to the log.
        String logged = origin.length() > MAX_LOGGED_ORIGIN_LENGTH
                ? origin.substring(0, MAX_LOGGED_ORIGIN_LENGTH) : origin;
        log.warn("Refused a request with an untrusted Origin: path={}, origin={}", request.getRequestURI(), logged);
        throw new OriginNotAllowedException();
    }

    private boolean isTrusted(String origin) {
        try {
            return trustedOrigins.contains(CorsProperties.normalizeOrigin(origin.trim(), "Origin header"));
        } catch (IllegalArgumentException e) {
            // Not an origin at all (for example the literal "null").
            return false;
        }
    }
}
