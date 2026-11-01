package com.tgrznar.javalearningapp.learning.coding;

import com.tgrznar.javalearningapp.learning.coding.dto.CodingSubmissionRequest;
import com.tgrznar.javalearningapp.learning.coding.dto.CodingSubmissionResponse;
import com.tgrznar.javalearningapp.learning.coding.dto.StudentCodingTaskDetail;
import com.tgrznar.javalearningapp.learning.coding.dto.StudentCodingTaskSummary;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Student-only coding task endpoints. Administrators and teachers get 403 here. */
@RestController
@RequestMapping("/api/v1/learning/modules/{moduleId}/coding-tasks")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class StudentCodingTaskController {

    private final StudentCodingTaskService taskService;

    @GetMapping
    public List<StudentCodingTaskSummary> list(@PathVariable Long moduleId,
                                               @AuthenticationPrincipal Long userId) {
        return taskService.list(userId, moduleId);
    }

    @GetMapping("/{taskId}")
    public StudentCodingTaskDetail detail(@PathVariable Long moduleId,
                                          @PathVariable Long taskId,
                                          @AuthenticationPrincipal Long userId) {
        return taskService.detail(userId, moduleId, taskId);
    }

    @PostMapping("/{taskId}/submissions")
    public CodingSubmissionResponse submit(@PathVariable Long moduleId,
                                           @PathVariable Long taskId,
                                           @Valid @RequestBody CodingSubmissionRequest request,
                                           @AuthenticationPrincipal Long userId) {
        return taskService.submit(userId, moduleId, taskId, request.code());
    }
}
