package com.tgrznar.javalearningapp.user.teacher;

import com.tgrznar.javalearningapp.user.teacher.dto.StudentOverviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Teacher-only access to the students of the teacher's own classes (UC-11). The role check covers every method
 * of the class.
 */
@RestController
@RequestMapping("/api/v1/teacher/students")
@PreAuthorize("hasRole('TEACHER')")
@RequiredArgsConstructor
public class TeacherStudentController {

    private final TeacherStudentService studentService;

    @GetMapping
    public StudentOverviewResponse overview(@RequestParam(required = false) Long classId,
                                            @RequestParam(required = false) Boolean active,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @AuthenticationPrincipal Long teacherId) {
        return studentService.overview(teacherId, classId, active, page, size);
    }
}
