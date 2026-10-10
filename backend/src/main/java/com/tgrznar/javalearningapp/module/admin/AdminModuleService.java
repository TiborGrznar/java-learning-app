package com.tgrznar.javalearningapp.module.admin;

import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.module.CourseModuleRepository;
import com.tgrznar.javalearningapp.module.dto.ModuleRequest;
import com.tgrznar.javalearningapp.module.dto.ModuleResponse;
import com.tgrznar.javalearningapp.module.dto.ModuleSummary;
import com.tgrznar.javalearningapp.module.exception.ModuleNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Module management by an administrator. Modules are never deleted, only deactivated. */
@Service
public class AdminModuleService {

    private static final Logger log = LoggerFactory.getLogger(AdminModuleService.class);

    private static final Sort LIST_ORDER = Sort.by("orderNumber", "id");

    private final CourseModuleRepository moduleRepository;

    public AdminModuleService(CourseModuleRepository moduleRepository) {
        this.moduleRepository = moduleRepository;
    }

    @Transactional
    public ModuleResponse create(ModuleRequest request, Long adminId) {
        CourseModule module = new CourseModule();
        apply(module, request);

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

    @Transactional
    public ModuleResponse setActive(Long id, boolean active, Long adminId) {
        CourseModule module = findOrThrow(id);
        if (module.isActive() == active) {
            return ModuleResponse.from(module);
        }

        module.setActive(active);
        CourseModule saved = moduleRepository.saveAndFlush(module);
        log.info("Module active flag changed: id={}, active={}, changedByAdminId={}", saved.getId(), active, adminId);
        return ModuleResponse.from(saved);
    }

    private CourseModule findOrThrow(Long id) {
        return moduleRepository.findById(id).orElseThrow(() -> new ModuleNotFoundException(id));
    }

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