package com.tgrznar.javalearningapp.codingtask.admin;

import com.tgrznar.javalearningapp.codingtask.dto.CodingTaskActiveRequest;
import com.tgrznar.javalearningapp.codingtask.dto.CodingTaskCaseResponse;
import com.tgrznar.javalearningapp.codingtask.dto.CodingTaskCasesRequest;
import com.tgrznar.javalearningapp.codingtask.dto.CodingTaskRequest;
import com.tgrznar.javalearningapp.codingtask.dto.CodingTaskResponse;
import com.tgrznar.javalearningapp.codingtask.dto.CodingTaskSummary;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Administrator-only coding task management. The role check covers every method of the class.
 * The path starts with /api/v1/modules, which is on the large-body list of the rate limiter,
 * so a task with code or twenty cases fits the request size limit.
 */
@RestController
@RequestMapping("/api/v1/modules/{moduleId}/coding-tasks")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminCodingTaskController {

    private final AdminCodingTaskService adminCodingTaskService;

    @PostMapping
    public ResponseEntity<CodingTaskResponse> create(@PathVariable Long moduleId,
                                                     @Valid @RequestBody CodingTaskRequest request,
                                                     @AuthenticationPrincipal Long adminId) {
        CodingTaskResponse created = adminCodingTaskService.create(moduleId, request, adminId);
        URI location = URI.create("/api/v1/modules/" + moduleId + "/coding-tasks/" + created.id());
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public List<CodingTaskSummary> list(@PathVariable Long moduleId) {
        return adminCodingTaskService.list(moduleId);
    }

    @GetMapping("/{id}")
    public CodingTaskResponse get(@PathVariable Long moduleId, @PathVariable Long id) {
        return adminCodingTaskService.get(moduleId, id);
    }

    @PutMapping("/{id}")
    public CodingTaskResponse update(@PathVariable Long moduleId,
                                     @PathVariable Long id,
                                     @Valid @RequestBody CodingTaskRequest request,
                                     @AuthenticationPrincipal Long adminId) {
        return adminCodingTaskService.update(moduleId, id, request, adminId);
    }

    @PatchMapping("/{id}/active")
    public CodingTaskResponse setActive(@PathVariable Long moduleId,
                                        @PathVariable Long id,
                                        @Valid @RequestBody CodingTaskActiveRequest request,
                                        @AuthenticationPrincipal Long adminId) {
        return adminCodingTaskService.setActive(moduleId, id, request.active(), adminId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long moduleId,
                       @PathVariable Long id,
                       @AuthenticationPrincipal Long adminId) {
        adminCodingTaskService.delete(moduleId, id, adminId);
    }

    @GetMapping("/{id}/cases")
    public List<CodingTaskCaseResponse> listCases(@PathVariable Long moduleId, @PathVariable Long id) {
        return adminCodingTaskService.listCases(moduleId, id);
    }

    @PutMapping("/{id}/cases")
    public List<CodingTaskCaseResponse> replaceCases(@PathVariable Long moduleId,
                                                     @PathVariable Long id,
                                                     @Valid @RequestBody CodingTaskCasesRequest request,
                                                     @AuthenticationPrincipal Long adminId) {
        return adminCodingTaskService.replaceCases(moduleId, id, request, adminId);
    }
}
