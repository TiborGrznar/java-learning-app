package com.tgrznar.javalearningapp.common;

import com.tgrznar.javalearningapp.schoolclass.enrollment.EnrollmentGateInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers request interceptors. A new learning endpoint must live under /api/v1/learning to be covered by the gate. */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final EnrollmentGateInterceptor enrollmentGateInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(enrollmentGateInterceptor).addPathPatterns("/api/v1/learning/**");
    }
}