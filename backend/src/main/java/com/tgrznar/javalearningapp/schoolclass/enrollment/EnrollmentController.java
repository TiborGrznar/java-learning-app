package com.tgrznar.javalearningapp.schoolclass.enrollment;

import com.tgrznar.javalearningapp.schoolclass.dto.EnrollmentRequest;
import com.tgrznar.javalearningapp.schoolclass.dto.EnrollmentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Student-only: joining a class with its join code. The role check covers every method of the class. */
@RestController
@RequestMapping("/api/v1/enrollments")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @PostMapping
    public EnrollmentResponse enroll(@Valid @RequestBody EnrollmentRequest request,
                                     @AuthenticationPrincipal Long studentId) {
        return enrollmentService.enroll(studentId, request.joinCode());
    }
}