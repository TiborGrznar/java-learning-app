package com.tgrznar.javalearningapp.schoolclass.teacher;

import com.tgrznar.javalearningapp.schoolclass.dto.ClassResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Teacher-only access to the teacher's own classes. The role check covers every method of the class. */
@RestController
@RequestMapping("/api/v1/teacher/classes")
@PreAuthorize("hasRole('TEACHER')")
@RequiredArgsConstructor
public class TeacherSchoolClassController {

    private final TeacherSchoolClassService classService;

    @GetMapping
    public List<ClassResponse> list(@RequestParam(required = false) Boolean active,
                                    @AuthenticationPrincipal Long teacherId) {
        return classService.list(teacherId, active);
    }

    @GetMapping("/{id}")
    public ClassResponse get(@PathVariable Long id, @AuthenticationPrincipal Long teacherId) {
        return classService.get(id, teacherId);
    }

    @PostMapping("/{id}/join-code")
    public ClassResponse regenerateJoinCode(@PathVariable Long id, @AuthenticationPrincipal Long teacherId) {
        return classService.regenerateJoinCode(id, teacherId);
    }
}