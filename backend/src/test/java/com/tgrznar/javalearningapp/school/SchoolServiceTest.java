package com.tgrznar.javalearningapp.school;

import com.tgrznar.javalearningapp.school.dto.SchoolRequest;
import com.tgrznar.javalearningapp.school.dto.SchoolResponse;
import com.tgrznar.javalearningapp.school.exception.SchoolNameAlreadyExistsException;
import com.tgrznar.javalearningapp.school.exception.SchoolNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchoolServiceTest {

    private static final Long ADMIN_ID = 99L;

    @Mock
    private SchoolRepository schoolRepository;

    @InjectMocks
    private SchoolService schoolService;

    // ---------- create ----------

    @Test
    void create_savesTrimmedNameAndAddress() {
        when(schoolRepository.existsByNameIgnoreCase("Gymnazium")).thenReturn(false);
        when(schoolRepository.saveAndFlush(any(School.class))).thenAnswer(inv -> {
            School s = inv.getArgument(0);
            s.setId(1L);
            return s;
        });

        SchoolResponse response = schoolService.create(new SchoolRequest("  Gymnazium  ", "  Hlavna 1  "), ADMIN_ID);

        ArgumentCaptor<School> captor = ArgumentCaptor.forClass(School.class);
        verify(schoolRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Gymnazium");
        assertThat(captor.getValue().getAddress()).isEqualTo("Hlavna 1");
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.active()).isTrue();
    }

    @Test
    void create_duplicateName_throwsAndDoesNotSave() {
        when(schoolRepository.existsByNameIgnoreCase("Gymnazium")).thenReturn(true);

        assertThatThrownBy(() -> schoolService.create(new SchoolRequest("Gymnazium", "Hlavna 1"), ADMIN_ID))
                .isInstanceOf(SchoolNameAlreadyExistsException.class);

        verify(schoolRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_uniqueIndexViolation_isTranslatedToNameAlreadyExists() {
        when(schoolRepository.existsByNameIgnoreCase("Gymnazium")).thenReturn(false);
        when(schoolRepository.saveAndFlush(any(School.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> schoolService.create(new SchoolRequest("Gymnazium", "Hlavna 1"), ADMIN_ID))
                .isInstanceOf(SchoolNameAlreadyExistsException.class);
    }

    // ---------- list / get ----------

    @Test
    void list_withoutFilter_returnsAllSchools() {
        when(schoolRepository.findAllByOrderByNameAsc()).thenReturn(List.of(school(1L, "A", true), school(2L, "B", false)));

        assertThat(schoolService.list(null)).hasSize(2);
        verify(schoolRepository, never()).findAllByActiveOrderByNameAsc(any(Boolean.class));
    }

    @Test
    void list_withActiveFilter_usesFilteredQuery() {
        when(schoolRepository.findAllByActiveOrderByNameAsc(true)).thenReturn(List.of(school(1L, "A", true)));

        List<SchoolResponse> result = schoolService.list(true);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).active()).isTrue();
        verify(schoolRepository, never()).findAllByOrderByNameAsc();
    }

    @Test
    void get_unknownId_throwsNotFound() {
        when(schoolRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> schoolService.get(5L)).isInstanceOf(SchoolNotFoundException.class);
    }

    // ---------- update ----------

    @Test
    void update_changesNameAndAddress() {
        School existing = school(1L, "Old", true);
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(schoolRepository.existsByNameIgnoreCaseAndIdNot("New", 1L)).thenReturn(false);
        when(schoolRepository.saveAndFlush(existing)).thenReturn(existing);

        SchoolResponse response = schoolService.update(1L, new SchoolRequest(" New ", " New street 2 "), ADMIN_ID);

        assertThat(response.name()).isEqualTo("New");
        assertThat(response.address()).isEqualTo("New street 2");
    }

    @Test
    void update_nameUsedByAnotherSchool_throws() {
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(school(1L, "Old", true)));
        when(schoolRepository.existsByNameIgnoreCaseAndIdNot("Taken", 1L)).thenReturn(true);

        assertThatThrownBy(() -> schoolService.update(1L, new SchoolRequest("Taken", "Street 1"), ADMIN_ID))
                .isInstanceOf(SchoolNameAlreadyExistsException.class);

        verify(schoolRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_unknownId_throwsNotFound() {
        when(schoolRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> schoolService.update(7L, new SchoolRequest("X", "Y"), ADMIN_ID))
                .isInstanceOf(SchoolNotFoundException.class);
    }

    // ---------- setActive ----------

    @Test
    void setActive_changesFlag() {
        School existing = school(1L, "A", true);
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(schoolRepository.save(existing)).thenReturn(existing);

        SchoolResponse response = schoolService.setActive(1L, false, ADMIN_ID);

        assertThat(response.active()).isFalse();
    }

    @Test
    void setActive_unknownId_throwsNotFound() {
        when(schoolRepository.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> schoolService.setActive(3L, true, ADMIN_ID))
                .isInstanceOf(SchoolNotFoundException.class);
    }

    private static School school(Long id, String name, boolean active) {
        School s = new School();
        s.setId(id);
        s.setName(name);
        s.setAddress("Address");
        s.setActive(active);
        return s;
    }
}