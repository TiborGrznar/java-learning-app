package com.tgrznar.javalearningapp.module.admin;

import com.tgrznar.javalearningapp.codingtask.CodingTaskRepository;
import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.module.CourseModuleRepository;
import com.tgrznar.javalearningapp.module.dto.ModuleRequest;
import com.tgrznar.javalearningapp.module.dto.ModuleResponse;
import com.tgrznar.javalearningapp.module.dto.ModuleSummary;
import com.tgrznar.javalearningapp.module.exception.ModuleNotFoundException;
import com.tgrznar.javalearningapp.module.exception.ModuleRequiresActiveTaskException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Module management by an administrator. Modules are never deleted, only deactivated.
 * A new module starts inactive, and a module can only be activated while it has an active
 * coding task, so students never see a module they cannot finish.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminModuleService {

    private static final Sort LIST_ORDER = Sort.by("orderNumber", "id");

    private final CourseModuleRepository moduleRepository;
    private final CodingTaskRepository codingTaskRepository;

    @Transactional
    public ModuleResponse create(ModuleRequest request, Long adminId) {
        CourseModule module = new CourseModule();
        apply(module, request);
        // The entity default is active, but a brand new module has no coding task yet.
        module.setActive(false);

        // Flush now so the id and createdAt are set and a database error surfaces here.
        CourseModule saved = moduleRepository.saveAndFlush(module);
        log.info("Module created: id={}, createdByAdminId={}", saved.getId(), adminId);
        return ModuleResponse.from(saved);
    }

    /** All modules, active and inactive, ordered by order number. */
    @Transactional(readOnly = true)
    public List<ModuleSummary> list() {
        return moduleRepository.findAll(LIST_ORDER).stream().map(ModuleSummary::from).toList();
    }

    @Transactional(readOnly = true)
    public ModuleResponse get(Long id) {
        return ModuleResponse.from(findOrThrow(id));
    }

    @Transactional
    public ModuleResponse update(Long id, ModuleRequest request, Long adminId) {
        CourseModule module = findOrThrow(id);
        apply(module, request);

        CourseModule saved = moduleRepository.saveAndFlush(module);
        log.info("Module updated: id={}, updatedByAdminId={}", saved.getId(), adminId);
        return ModuleResponse.from(saved);
    }

    /** Deactivation is always allowed; activation needs at least one active coding task. */
    @Transactional
    public ModuleResponse setActive(Long id, boolean active, Long adminId) {
        CourseModule module = findOrThrow(id);
        if (module.isActive() == active) {
            return ModuleResponse.from(module);
        }
        if (active && !codingTaskRepository.existsByModuleIdAndActiveTrue(id)) {
            throw new ModuleRequiresActiveTaskException(id);
        }

        module.setActive(active);
        CourseModule saved = moduleRepository.saveAndFlush(module);
        log.info("Module active flag changed: id={}, active={}, changedByAdminId={}", saved.getId(), active, adminId);
        return ModuleResponse.from(saved);
    }

    private CourseModule findOrThrow(Long id) {
        return moduleRepository.findById(id).orElseThrow(() -> new ModuleNotFoundException(id));
    }

    /** Copies the request into the entity. Never touches id, active or createdAt. */
    private static void apply(CourseModule module, ModuleRequest request) {
        module.setTitle(request.title().trim());
        module.setDescription(trimToNull(request.description()));
        // The theory is Markdown: indentation and trailing spaces can matter, so it is stored untrimmed.
        module.setTheoryContent(blankToNull(request.theoryContent()));
        module.setOrderNumber(request.orderNumber());
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}