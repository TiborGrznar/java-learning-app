package com.tgrznar.javalearningapp.auth.security;

import com.tgrznar.javalearningapp.user.model.UserAccess;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Authenticates requests carrying a valid JWT access token in the Authorization header.
 * <p>
 * The token proves who the caller is (the user id), nothing else. Whether the account is still active and which
 * role it has now is read from the database on every request (one query by primary key, two columns), so a
 * deactivated, deleted or demoted user loses access at once and not when the 15-minute token expires. The role
 * claim in the token is never used for authorization. A user who is missing or inactive stays unauthenticated,
 * the security config then answers 401, and a database error is not swallowed (the request fails, it is never
 * let through).
 * <p>
 * Deliberately not a Spring bean: it is created inside SecurityConfig, otherwise
 * Spring Boot would also register it as a plain servlet filter and run it twice.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            Long userId = verifiedUserId(header.substring(BEARER_PREFIX.length()));
            Optional<UserAccess> access = userId == null ? Optional.empty() : userRepository.findAccessById(userId);

            if (access.isPresent() && access.get().active()) {
                // The principal is just the user id, the authority comes from the database.
                var authentication = new UsernamePasswordAuthenticationToken(userId, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + access.get().role().name())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                // Invalid token, or a user who does not exist or is deactivated: stay unauthenticated,
                // the security config then answers 401 for protected endpoints.
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    /** The user id of a token with a valid signature that has not expired, otherwise null. */
    private Long verifiedUserId(String token) {
        try {
            return Long.valueOf(jwtService.parseAccessToken(token).getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            // Invalid, expired or malformed token (a subject that is not a number included).
            return null;
        }
    }
}