package com.tgrznar.javalearningapp.quizquestion.admin;

import com.tgrznar.javalearningapp.quizquestion.dto.QuestionRequest;
import com.tgrznar.javalearningapp.quizquestion.dto.QuestionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/** Administrator-only quiz question management. The role check covers every method of the class. */
@RestController
@RequestMapping("/api/v1/modules/{moduleId}/questions")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminQuestionController {

    private final AdminQuestionService adminQuestionService;

    @PostMapping
    public ResponseEntity<QuestionResponse> create(@PathVariable Long moduleId,
                                                   @Valid @RequestBody QuestionRequest request,
                                                   @AuthenticationPrincipal Long adminId) {
        QuestionResponse created = adminQuestionService.create(moduleId, request, adminId);
        URI location = URI.create("/api/v1/modules/" + moduleId + "/questions/" + created.id());
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public List<QuestionResponse> list(@PathVariable Long moduleId) {
        return adminQuestionService.list(moduleId);
    }

    @GetMapping("/{id}")
    public QuestionResponse get(@PathVariable Long moduleId, @PathVariable Long id) {
        return adminQuestionService.get(moduleId, id);
    }

    @PutMapping("/{id}")
    public QuestionResponse update(@PathVariable Long moduleId,
                                   @PathVariable Long id,
                                   @Valid @RequestBody QuestionRequest request,
                                   @AuthenticationPrincipal Long adminId) {
        return adminQuestionService.update(moduleId, id, request, adminId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long moduleId,
                       @PathVariable Long id,
                       @AuthenticationPrincipal Long adminId) {
        adminQuestionService.delete(moduleId, id, adminId);
    }
}