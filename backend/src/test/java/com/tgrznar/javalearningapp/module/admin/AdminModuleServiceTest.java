package com.tgrznar.javalearningapp.module.admin;

import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.module.CourseModuleRepository;
import com.tgrznar.javalearningapp.module.dto.ModuleRequest;
import com.tgrznar.javalearningapp.module.dto.ModuleResponse;
import com.tgrznar.javalearningapp.module.dto.ModuleSummary;
import com.tgrznar.javalearningapp.module.exception.ModuleNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminModuleServiceTest {

    private static final Long ADMIN_ID = 99L;

    @Mock
    private CourseModuleRepository moduleRepository;

    @InjectMocks
    private AdminModuleService service;

    // ---------- create ----------

    @Test
    void create_trimsTitleAndStoresBlankFieldsAsNull() {
        when(moduleRepository.saveAndFlush(any(CourseModule.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(new ModuleRequest("  Cykly  ", "   ", "   ", 3), ADMIN_ID);

        ArgumentCaptor<CourseModule> captor = ArgumentCaptor.forClass(CourseModule.class);
        verify(moduleRepository).saveAndFlush(captor.capture());
        CourseModule saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("Cykly");
        assertThat(saved.getDescription()).isNull();
        assertThat(saved.getTheoryContent()).isNull();
        assertThat(saved.getOrderNumber()).isEqualTo(3);
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void create_storesTheoryUntrimmed() {
        when(moduleRepository.saveAndFlush(any(CourseModule.class))).thenAnswer(inv -> inv.getArgument(0));
        String theory = "  # Nadpis\n\n    kod\n";

        service.create(new ModuleRequest("Teoria", "Popis", theory, 1), ADMIN_ID);

        ArgumentCaptor<CourseModule> captor = ArgumentCaptor.forClass(CourseModule.class);
        verify(moduleRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getTheoryContent()).isEqualTo(theory);
    }

    @Test
    void create_returnsTheSavedModule() {
        when(moduleRepository.saveAndFlush(any(CourseModule.class))).thenAnswer(inv -> {
            CourseModule m = inv.getArgument(0);
            m.setId(7L);
            return m;
        });

        ModuleResponse response = service.create(new ModuleRequest("Uvod", "Popis", "# Teoria", 1), ADMIN_ID);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.title()).isEqualTo("Uvod");
        assertThat(response.theoryContent()).isEqualTo("# Teoria");
        assertThat(response.active()).isTrue();
    }

    // ---------- list ----------

    @Test
    void list_returnsSummariesInRepositoryOrder() {
        when(moduleRepository.findAll(Sort.by("orderNumber", "id")))
                .thenReturn(List.of(module(1L, true), module(2L, false)));

        List<ModuleSummary> result = service.list();

        assertThat(result).extracting(ModuleSummary::id).containsExactly(1L, 2L);
        assertThat(result.get(0).active()).isTrue();
        assertThat(result.get(1).active()).isFalse();
    }

    // ---------- get ----------

    @Test
    void get_returnsFullModule() {
        when(moduleRepository.findById(5L)).thenReturn(Optional.of(module(5L, true)));

        ModuleResponse response = service.get(5L);

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.theoryContent()).isEqualTo("# Theory");
    }

    @Test
    void get_unknownModule_throwsNotFound() {
        when(moduleRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(5L)).isInstanceOf(ModuleNotFoundException.class);
    }

    // ---------- update ----------

    @Test
    void update_replacesAllFields() {
        when(moduleRepository.findById(5L)).thenReturn(Optional.of(module(5L, true)));
        when(moduleRepository.saveAndFlush(any(CourseModule.class))).thenAnswer(inv -> inv.getArgument(0));

        ModuleResponse response = service.update(5L,
                new ModuleRequest(" New title ", "New description", "## New theory", 7), ADMIN_ID);

        assertThat(response.title()).isEqualTo("New title");
        assertThat(response.description()).isEqualTo("New description");
        assertThat(response.theoryContent()).isEqualTo("## New theory");
        assertThat(response.orderNumber()).isEqualTo(7);
    }

    @Test
    void update_keepsTheActiveFlag() {
        when(moduleRepository.findById(5L)).thenReturn(Optional.of(module(5L, false)));
        when(moduleRepository.saveAndFlush(any(CourseModule.class))).thenAnswer(inv -> inv.getArgument(0));

        ModuleResponse response = service.update(5L, new ModuleRequest("Title", "Description", "# Theory", 1), ADMIN_ID);

        assertThat(response.active()).isFalse();
    }

    @Test
    void update_unknownModule_throwsNotFoundAndSavesNothing() {
        when(moduleRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(5L, new ModuleRequest("Title", null, null, 1), ADMIN_ID))
                .isInstanceOf(ModuleNotFoundException.class);

        verify(moduleRepository, never()).saveAndFlush(any());
    }

    // ---------- setActive ----------

    @Test
    void setActive_deactivates() {
        when(moduleRepository.findById(5L)).thenReturn(Optional.of(module(5L, true)));
        when(moduleRepository.saveAndFlush(any(CourseModule.class))).thenAnswer(inv -> inv.getArgument(0));

        ModuleResponse response = service.setActive(5L, false, ADMIN_ID);

        assertThat(response.active()).isFalse();
    }

    @Test
    void setActive_reactivates() {
        when(moduleRepository.findById(5L)).thenReturn(Optional.of(module(5L, false)));
        when(moduleRepository.saveAndFlush(any(CourseModule.class))).thenAnswer(inv -> inv.getArgument(0));

        ModuleResponse response = service.setActive(5L, true, ADMIN_ID);

        assertThat(response.active()).isTrue();
    }

    @Test
    void setActive_sameState_isNoOp() {
        when(moduleRepository.findById(5L)).thenReturn(Optional.of(module(5L, true)));

        ModuleResponse response = service.setActive(5L, true, ADMIN_ID);

        assertThat(response.active()).isTrue();
        verify(moduleRepository, never()).saveAndFlush(any());
    }

    @Test
    void setActive_unknownModule_throwsNotFound() {
        when(moduleRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setActive(5L, false, ADMIN_ID))
                .isInstanceOf(ModuleNotFoundException.class);
    }

    // ---------- helpers ----------

    private static CourseModule module(Long id, boolean active) {
        CourseModule m = new CourseModule();
        m.setId(id);
        m.setTitle("Module " + id);
        m.setDescription("Description");
        m.setTheoryContent("# Theory");
        m.setOrderNumber(id.intValue());
        m.setActive(active);
        return m;
    }
}