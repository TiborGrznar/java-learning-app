package com.tgrznar.javalearningapp.schoolclass.admin;

import com.tgrznar.javalearningapp.schoolclass.dto.ClassActiveRequest;
import com.tgrznar.javalearningapp.schoolclass.dto.ClassResponse;
import com.tgrznar.javalearningapp.schoolclass.dto.CreateClassRequest;
import com.tgrznar.javalearningapp.schoolclass.dto.UpdateClassRequest;
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

/** Administrator-only class management. The role check covers every method of the class. */
@RestController
@RequestMapping("/api/v1/classes")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSchoolClassController {

    private final AdminSchoolClassService classService;

    @PostMapping
    public ResponseEntity<ClassResponse> create(@Valid @RequestBody CreateClassRequest request,
                                                @AuthenticationPrincipal Long adminId) {
        ClassResponse created = classService.create(request, adminId);
        return ResponseEntity.created(URI.create("/api/v1/classes/" + created.id())).body(created);
    }

    @GetMapping
    public List<ClassResponse> list(@RequestParam(required = false) Long schoolId,
                                    @RequestParam(required = false) Long teacherId,
                                    @RequestParam(required = false) Boolean active) {
        return classService.list(schoolId, teacherId, active);
    }

    @GetMapping("/{id}")
    public ClassResponse get(@PathVariable Long id) {
        return classService.get(id);
    }

    @PutMapping("/{id}")
    public ClassResponse update(@PathVariable Long id,
                                @Valid @RequestBody UpdateClassRequest request,
                                @AuthenticationPrincipal Long adminId) {
        return classService.update(id, request, adminId);
    }

    @PatchMapping("/{id}/active")
    public ClassResponse setActive(@PathVariable Long id,
                                   @Valid @RequestBody ClassActiveRequest request,
                                   @AuthenticationPrincipal Long adminId) {
        return classService.setActive(id, request.active(), adminId);
    }

    @PostMapping("/{id}/join-code")
    public ClassResponse regenerateJoinCode(@PathVariable Long id, @AuthenticationPrincipal Long adminId) {
        return classService.regenerateJoinCode(id, adminId);
    }
}