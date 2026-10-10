package com.tgrznar.javalearningapp.school;

import com.tgrznar.javalearningapp.school.dto.SchoolActiveRequest;
import com.tgrznar.javalearningapp.school.dto.SchoolRequest;
import com.tgrznar.javalearningapp.school.dto.SchoolResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/schools")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class SchoolController {

    private final SchoolService schoolService;

    @PostMapping
    public ResponseEntity<SchoolResponse> create(@Valid @RequestBody SchoolRequest request,
                                                 @AuthenticationPrincipal Long adminId) {
        SchoolResponse created = schoolService.create(request, adminId);
        return ResponseEntity.created(URI.create("/api/v1/schools/" + created.id())).body(created);
    }

    @GetMapping
    public List<SchoolResponse> list(@RequestParam(required = false) Boolean active) {
        return schoolService.list(active);
    }

    @GetMapping("/{id}")
    public SchoolResponse get(@PathVariable Long id) {
        return schoolService.get(id);
    }

    @PutMapping("/{id}")
    public SchoolResponse update(@PathVariable Long id,
                                 @Valid @RequestBody SchoolRequest request,
                                 @AuthenticationPrincipal Long adminId) {
        return schoolService.update(id, request, adminId);
    }

    @PatchMapping("/{id}/active")
    public SchoolResponse setActive(@PathVariable Long id,
                                    @Valid @RequestBody SchoolActiveRequest request,
                                    @AuthenticationPrincipal Long adminId) {
        return schoolService.setActive(id, request.active(), adminId);
    }
}