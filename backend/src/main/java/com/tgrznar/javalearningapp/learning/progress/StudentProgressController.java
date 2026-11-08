package com.tgrznar.javalearningapp.learning.progress;

import com.tgrznar.javalearningapp.learning.progress.dto.ProgressResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The student's own progress (UC-10). Student-only, like the rest of /api/v1/learning: administrators and
 * teachers get 403. There is no user id in the URL, the progress is always the one of the signed-in student.
 */
@RestController
@RequestMapping("/api/v1/learning/progress")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class StudentProgressController {

    private final StudentProgressService progressService;

    @GetMapping
    public ProgressResponse get(@AuthenticationPrincipal Long userId) {
        return progressService.get(userId);
    }
}
