package com.tgrznar.javalearningapp.schoolclass.teacher;

import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.schoolclass.JoinCodeGenerator;
import com.tgrznar.javalearningapp.schoolclass.SchoolClass;
import com.tgrznar.javalearningapp.schoolclass.SchoolClassRepository;
import com.tgrznar.javalearningapp.schoolclass.dto.ClassResponse;
import com.tgrznar.javalearningapp.schoolclass.exception.SchoolClassNotFoundException;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherSchoolClassServiceTest {

    private static final Long TEACHER_ID = 7L;
    private static final Long CLASS_ID = 20L;

    @Mock
    private SchoolClassRepository classRepository;

    @Mock
    private JoinCodeGenerator joinCodeGenerator;

    @InjectMocks
    private TeacherSchoolClassService service;

    // ---------- list ----------

    @Test
    void list_searchesOnlyClassesOfTheCallingTeacher() {
        User teacher = teacher(TEACHER_ID);
        when(classRepository.search(isNull(), eq(TEACHER_ID), eq(true)))
                .thenReturn(List.of(schoolClass(1L, teacher, "1.A", "AAAAAAA2", true),
                        schoolClass(2L, teacher, "1.B", "BBBBBBB3", true)));

        List<ClassResponse> response = service.list(TEACHER_ID, true);

        assertThat(response).extracting(ClassResponse::name).containsExactly("1.A", "1.B");
        assertThat(response).extracting(ClassResponse::teacherId).containsOnly(TEACHER_ID);
    }

    @Test
    void list_nullActiveFilterMeansAny() {
        when(classRepository.search(isNull(), eq(TEACHER_ID), isNull())).thenReturn(List.of());

        List<ClassResponse> response = service.list(TEACHER_ID, null);

        assertThat(response).isEmpty();
        verify(classRepository).search(isNull(), eq(TEACHER_ID), isNull());
    }

    // ---------- get ----------

    @Test
    void get_ownClass_returnsDetailWithJoinCode() {
        when(classRepository.findByIdAndTeacherId(CLASS_ID, TEACHER_ID))
                .thenReturn(Optional.of(schoolClass(CLASS_ID, teacher(TEACHER_ID), "1.A", "AAAAAAA2", true)));

        ClassResponse response = service.get(CLASS_ID, TEACHER_ID);

        assertThat(response.id()).isEqualTo(CLASS_ID);
        assertThat(response.joinCode()).isEqualTo("AAAAAAA2");
        assertThat(response.teacherId()).isEqualTo(TEACHER_ID);
    }

    @Test
    void get_foreignOrUnknownClass_throwsNotFound() {
        when(classRepository.findByIdAndTeacherId(CLASS_ID, TEACHER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(CLASS_ID, TEACHER_ID))
                .isInstanceOf(SchoolClassNotFoundException.class);
    }

    // ---------- regenerateJoinCode ----------

    @Test
    void regenerateJoinCode_ownClass_replacesCode() {
        SchoolClass own = schoolClass(CLASS_ID, teacher(TEACHER_ID), "1.A", "OLDCODE2", true);
        when(classRepository.findByIdAndTeacherId(CLASS_ID, TEACHER_ID)).thenReturn(Optional.of(own));
        when(joinCodeGenerator.generateUnique()).thenReturn("NEWCODE3");
        when(classRepository.saveAndFlush(any(SchoolClass.class))).thenAnswer(inv -> inv.getArgument(0));

        ClassResponse response = service.regenerateJoinCode(CLASS_ID, TEACHER_ID);

        assertThat(response.joinCode()).isEqualTo("NEWCODE3");
        assertThat(own.getJoinCode()).isEqualTo("NEWCODE3");
    }

    @Test
    void regenerateJoinCode_inactiveOwnClass_isAllowed() {
        SchoolClass own = schoolClass(CLASS_ID, teacher(TEACHER_ID), "1.A", "OLDCODE2", false);
        when(classRepository.findByIdAndTeacherId(CLASS_ID, TEACHER_ID)).thenReturn(Optional.of(own));
        when(joinCodeGenerator.generateUnique()).thenReturn("NEWCODE3");
        when(classRepository.saveAndFlush(any(SchoolClass.class))).thenAnswer(inv -> inv.getArgument(0));

        ClassResponse response = service.regenerateJoinCode(CLASS_ID, TEACHER_ID);

        assertThat(response.joinCode()).isEqualTo("NEWCODE3");
        assertThat(response.active()).isFalse();
    }

    @Test
    void regenerateJoinCode_foreignClass_throwsNotFoundAndChangesNothing() {
        when(classRepository.findByIdAndTeacherId(CLASS_ID, TEACHER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.regenerateJoinCode(CLASS_ID, TEACHER_ID))
                .isInstanceOf(SchoolClassNotFoundException.class);

        verifyNoInteractions(joinCodeGenerator);
        verify(classRepository, never()).saveAndFlush(any());
    }

    // ---------- helpers ----------

    private static User teacher(Long id) {
        User u = new User();
        u.setId(id);
        u.setName("Jana");
        u.setSurname("Novakova");
        u.setEmail("teacher" + id + "@example.com");
        u.setRole(UserRole.TEACHER);
        u.setActive(true);
        return u;
    }

    private static School school() {
        School s = new School();
        s.setId(5L);
        s.setName("School 5");
        s.setAddress("Address");
        s.setActive(true);
        return s;
    }

    private static SchoolClass schoolClass(Long id, User teacher, String name, String joinCode, boolean active) {
        SchoolClass c = new SchoolClass();
        c.setId(id);
        c.setSchool(school());
        c.setTeacher(teacher);
        c.setName(name);
        c.setJoinCode(joinCode);
        c.setActive(active);
        return c;
    }
}