package com.tgrznar.javalearningapp.schoolclass.enrollment;

import com.tgrznar.javalearningapp.schoolclass.exception.NotEnrolledException;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrollmentGateInterceptorTest {

    private static final Long USER_ID = 7L;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private EnrollmentGateInterceptor interceptor;

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final Object handler = new Object();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void preHandle_studentWithClass_letsTheRequestThrough() {
        authenticate(USER_ID, "STUDENT");
        when(userRepository.existsByIdAndSchoolClassIsNotNull(USER_ID)).thenReturn(true);

        assertTrue(interceptor.preHandle(request, response, handler));
    }

    @Test
    void preHandle_studentWithoutClass_throwsNotEnrolled() {
        authenticate(USER_ID, "STUDENT");
        when(userRepository.existsByIdAndSchoolClassIsNotNull(USER_ID)).thenReturn(false);

        assertThrows(NotEnrolledException.class, () -> interceptor.preHandle(request, response, handler));
        verify(userRepository).existsByIdAndSchoolClassIsNotNull(USER_ID);
    }

    @Test
    void preHandle_teacher_passesWithoutTouchingTheDatabase() {
        authenticate(USER_ID, "TEACHER");

        assertTrue(interceptor.preHandle(request, response, handler));
        verifyNoInteractions(userRepository);
    }

    @Test
    void preHandle_admin_passesWithoutTouchingTheDatabase() {
        authenticate(USER_ID, "ADMIN");

        assertTrue(interceptor.preHandle(request, response, handler));
        verifyNoInteractions(userRepository);
    }

    @Test
    void preHandle_withoutAuthentication_passesWithoutTouchingTheDatabase() {
        assertTrue(interceptor.preHandle(request, response, handler));
        verifyNoInteractions(userRepository);
    }

    @Test
    void preHandle_studentWithUnexpectedPrincipal_failsClosed() {
        authenticate("not-a-user-id", "STUDENT");

        assertThrows(AccessDeniedException.class, () -> interceptor.preHandle(request, response, handler));
        verifyNoInteractions(userRepository);
    }

    private static void authenticate(Object principal, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}