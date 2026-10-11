package com.tgrznar.javalearningapp.module.admin;

import com.tgrznar.javalearningapp.module.dto.ModuleActiveRequest;
import com.tgrznar.javalearningapp.module.dto.ModuleRequest;
import com.tgrznar.javalearningapp.module.dto.ModuleResponse;
import com.tgrznar.javalearningapp.module.dto.ModuleSummary;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/** Administrator-only module management. The role check covers every method of the class. */
@RestController
@RequestMapping("/api/v1/modules")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminModuleController {

    private final AdminModuleService adminModuleService;

    @PostMapping
    public ResponseEntity<ModuleResponse> create(@Valid @RequestBody ModuleRequest request,
                                                 @AuthenticationPrincipal Long adminId) {
        ModuleResponse created = adminModuleService.create(request, adminId);
        return ResponseEntity.created(URI.create("/api/v1/modules/" + created.id())).body(created);
    }

    @GetMapping
    public List<ModuleSummary> list() {
        return adminModuleService.list();
    }

    @GetMapping("/{id}")
    public ModuleResponse get(@PathVariable Long id) {
        return adminModuleService.get(id);
    }

    @PutMapping("/{id}")
    public ModuleResponse update(@PathVariable Long id,
                                 @Valid @RequestBody ModuleRequest request,
                                 @AuthenticationPrincipal Long adminId) {
        return adminModuleService.update(id, request, adminId);
    }

    @PatchMapping("/{id}/active")
    public ModuleResponse setActive(@PathVariable Long id,
                                    @Valid @RequestBody ModuleActiveRequest request,
                                    @AuthenticationPrincipal Long adminId) {
        return adminModuleService.setActive(id, request.active(), adminId);
    }
}