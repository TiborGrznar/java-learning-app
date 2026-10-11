package com.tgrznar.javalearningapp.learning;

import com.tgrznar.javalearningapp.learning.dto.StudentModuleDetail;
import com.tgrznar.javalearningapp.learning.dto.StudentModuleSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Student-only module browsing and progress. Administrators and teachers get 403 here, they manage content elsewhere. */
@RestController
@RequestMapping("/api/v1/learning/modules")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class StudentModuleController {

    private final StudentModuleService studentModuleService;
    private final ModuleProgressService moduleProgressService;

    @GetMapping
    public List<StudentModuleSummary> list(@AuthenticationPrincipal Long userId) {
        return studentModuleService.list(userId);
    }

    @GetMapping("/{id}")
    public StudentModuleDetail get(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
        return studentModuleService.get(userId, id);
    }

    /** Returns the module again so the client gets the new status without another request. */
    @PostMapping("/{id}/theory-completed")
    public StudentModuleDetail completeTheory(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
        moduleProgressService.markTheoryCompleted(userId, id);
        return studentModuleService.get(userId, id);
    }
}