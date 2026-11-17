package com.tgrznar.javalearningapp.common;

import com.tgrznar.javalearningapp.auth.cookie.OriginCheckInterceptor;
import com.tgrznar.javalearningapp.schoolclass.enrollment.EnrollmentGateInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers request interceptors. A new learning endpoint must live under /api/v1/learning to be covered by the gate.
 * A new endpoint that reads or sets the refresh token cookie must be added to the Origin check list.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final EnrollmentGateInterceptor enrollmentGateInterceptor;
    private final OriginCheckInterceptor originCheckInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(enrollmentGateInterceptor).addPathPatterns("/api/v1/learning/**");
        registry.addInterceptor(originCheckInterceptor).addPathPatterns(
                "/api/v1/auth/login",
                "/api/v1/auth/refresh",
                "/api/v1/auth/logout");
    }
}