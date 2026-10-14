package com.tgrznar.javalearningapp.schoolclass.enrollment;

import com.tgrznar.javalearningapp.schoolclass.exception.NotEnrolledException;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.user.model.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Lets a student reach the learning content only after joining a class.
 * <p>
 * Registered for /api/v1/learning/** in WebMvcConfig, so every endpoint under that prefix is covered
 * automatically, including future ones. The class is read from the database on every request, not from
 * the token, so joining or leaving a class applies at once.
 * <p>
 * Other roles pass untouched: the role check on the controller (@PreAuthorize) rejects them afterwards
 * with ACCESS_DENIED. The check runs before the controller method, so a student without a class gets
 * NOT_ENROLLED even for an unknown module or an invalid body, and learns nothing about the content.
 * A student whose class was deactivated later keeps access (deactivation only closes the class for new members).
 */
@Component
@RequiredArgsConstructor
public class EnrollmentGateInterceptor implements HandlerInterceptor {

    private static final String STUDENT_AUTHORITY = "ROLE_" + UserRole.STUDENT.name();

    private final UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !isStudent(authentication)) {
            // Anonymous callers are stopped earlier by the security filter chain, other roles by @PreAuthorize.
            return true;
        }
        // Fail closed: a student whose principal is not a user id cannot be checked, so the request is refused.
        if (!(authentication.getPrincipal() instanceof Long userId)) {
            throw new AccessDeniedException("Unexpected principal of a student");
        }
        if (!userRepository.existsByIdAndSchoolClassIsNotNull(userId)) {
            throw new NotEnrolledException();
        }
        return true;
    }

    private static boolean isStudent(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> STUDENT_AUTHORITY.equals(authority.getAuthority()));
    }
}