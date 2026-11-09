package com.tgrznar.javalearningapp.user.teacher;

import com.tgrznar.javalearningapp.user.teacher.dto.StudentOverviewResponse;
import com.tgrznar.javalearningapp.user.teacher.dto.StudentsActiveRequest;
import com.tgrznar.javalearningapp.user.teacher.dto.StudentsActiveResponse;
import com.tgrznar.javalearningapp.user.teacher.dto.TeacherStudentProgressResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
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

    @GetMapping("/{studentId}/progress")
    public TeacherStudentProgressResponse progress(@PathVariable Long studentId,
                                                   @AuthenticationPrincipal Long teacherId) {
        return studentService.progress(teacherId, studentId);
    }

    @PatchMapping("/active")
    public StudentsActiveResponse setActive(@Valid @RequestBody StudentsActiveRequest request,
                                            @AuthenticationPrincipal Long teacherId) {
        return studentService.setActive(teacherId, request.studentIds(), request.active());
    }
}