package com.tgrznar.javalearningapp.learning.quiz;

import com.tgrznar.javalearningapp.learning.quiz.dto.QuizResponse;
import com.tgrznar.javalearningapp.learning.quiz.dto.QuizResultResponse;
import com.tgrznar.javalearningapp.learning.quiz.dto.QuizSubmissionRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Student-only quiz endpoints. Administrators and teachers get 403 here. */
@RestController
@RequestMapping("/api/v1/learning/modules/{moduleId}/quiz")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class StudentQuizController {

    private final StudentQuizService quizService;

    @GetMapping
    public ResponseEntity<QuizResponse> load(@PathVariable Long moduleId,
                                             @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok()
                // Every load draws a different quiz, so a cached copy would be wrong.
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(quizService.load(userId, moduleId));
    }

    @PostMapping("/submissions")
    public QuizResultResponse submit(@PathVariable Long moduleId,
                                     @Valid @RequestBody QuizSubmissionRequest request,
                                     @AuthenticationPrincipal Long userId) {
        return quizService.submit(userId, moduleId, request);
    }
}