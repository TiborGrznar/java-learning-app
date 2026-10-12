package com.tgrznar.javalearningapp.schoolclass.admin;

import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.school.SchoolRepository;
import com.tgrznar.javalearningapp.school.exception.SchoolInactiveException;
import com.tgrznar.javalearningapp.school.exception.SchoolNotFoundException;
import com.tgrznar.javalearningapp.schoolclass.JoinCodeGenerator;
import com.tgrznar.javalearningapp.schoolclass.SchoolClass;
import com.tgrznar.javalearningapp.schoolclass.SchoolClassRepository;
import com.tgrznar.javalearningapp.schoolclass.dto.ClassResponse;
import com.tgrznar.javalearningapp.schoolclass.dto.CreateClassRequest;
import com.tgrznar.javalearningapp.schoolclass.dto.UpdateClassRequest;
import com.tgrznar.javalearningapp.schoolclass.exception.ClassNameAlreadyExistsException;
import com.tgrznar.javalearningapp.schoolclass.exception.InvalidClassDataException;
import com.tgrznar.javalearningapp.schoolclass.exception.SchoolClassNotFoundException;
import com.tgrznar.javalearningapp.user.exception.UserNotFoundException;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminSchoolClassServiceTest {

    private static final Long ADMIN_ID = 99L;

    @Mock
    private SchoolClassRepository classRepository;
    @Mock
    private SchoolRepository schoolRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JoinCodeGenerator joinCodeGenerator;

    private AdminSchoolClassService service;

    @BeforeEach
    void setUp() {
        service = new AdminSchoolClassService(classRepository, schoolRepository, userRepository, joinCodeGenerator);
    }

    @Test
    void create_validRequest_savesClassWithGeneratedCode() {
        School school = school(1L, true);
        User teacher = user(2L, UserRole.TEACHER, school, true);
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(school));
        when(userRepository.findById(2L)).thenReturn(Optional.of(teacher));
        when(classRepository.existsBySchoolIdAndNameIgnoreCase(1L, "3.A")).thenReturn(false);
        when(joinCodeGenerator.generateUnique()).thenReturn("ABCD2345");
        when(classRepository.saveAndFlush(any(SchoolClass.class))).thenAnswer(inv -> inv.getArgument(0));

        ClassResponse response = service.create(new CreateClassRequest("  3.A ", 1L, 2L), ADMIN_ID);

        assertThat(response.name()).isEqualTo("3.A");
        assertThat(response.joinCode()).isEqualTo("ABCD2345");
        assertThat(response.schoolId()).isEqualTo(1L);
        assertThat(response.teacherId()).isEqualTo(2L);
        assertThat(response.teacherName()).isEqualTo("Jano Novak");
        assertThat(response.active()).isTrue();
    }

    @Test
    void create_unknownSchool_throws() {
        when(schoolRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CreateClassRequest("3.A", 1L, 2L), ADMIN_ID))
                .isInstanceOf(SchoolNotFoundException.class);
    }

    @Test
    void create_inactiveSchool_throws() {
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(school(1L, false)));

        assertThatThrownBy(() -> service.create(new CreateClassRequest("3.A", 1L, 2L), ADMIN_ID))
                .isInstanceOf(SchoolInactiveException.class);
    }

    @Test
    void create_unknownTeacher_throws() {
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(school(1L, true)));
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CreateClassRequest("3.A", 1L, 2L), ADMIN_ID))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void create_userIsNotTeacher_throws() {
        School school = school(1L, true);
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(school));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, UserRole.STUDENT, school, true)));

        assertThatThrownBy(() -> service.create(new CreateClassRequest("3.A", 1L, 2L), ADMIN_ID))
                .isInstanceOfSatisfying(InvalidClassDataException.class,
                        ex -> assertThat(ex.getField()).isEqualTo("teacherId"));
    }

    @Test
    void create_inactiveTeacher_throws() {
        School school = school(1L, true);
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(school));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, UserRole.TEACHER, school, false)));

        assertThatThrownBy(() -> service.create(new CreateClassRequest("3.A", 1L, 2L), ADMIN_ID))
                .isInstanceOfSatisfying(InvalidClassDataException.class,
                        ex -> assertThat(ex.getField()).isEqualTo("teacherId"));
    }

    @Test
    void create_teacherFromOtherSchool_throws() {
        School school = school(1L, true);
        School otherSchool = school(5L, true);
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(school));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, UserRole.TEACHER, otherSchool, true)));

        assertThatThrownBy(() -> service.create(new CreateClassRequest("3.A", 1L, 2L), ADMIN_ID))
                .isInstanceOfSatisfying(InvalidClassDataException.class,
                        ex -> assertThat(ex.getField()).isEqualTo("teacherId"));
    }

    @Test
    void create_duplicateName_throwsWithoutSaving() {
        School school = school(1L, true);
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(school));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, UserRole.TEACHER, school, true)));
        when(classRepository.existsBySchoolIdAndNameIgnoreCase(1L, "3.A")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateClassRequest("3.A", 1L, 2L), ADMIN_ID))
                .isInstanceOf(ClassNameAlreadyExistsException.class);
        verify(classRepository, never()).saveAndFlush(any(SchoolClass.class));
        verify(joinCodeGenerator, never()).generateUnique();
    }

    @Test
    void create_databaseRejectsDuplicateName_throws() {
        School school = school(1L, true);
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(school));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, UserRole.TEACHER, school, true)));
        when(classRepository.existsBySchoolIdAndNameIgnoreCase(1L, "3.A")).thenReturn(false);
        when(joinCodeGenerator.generateUnique()).thenReturn("ABCD2345");
        when(classRepository.saveAndFlush(any(SchoolClass.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.create(new CreateClassRequest("3.A", 1L, 2L), ADMIN_ID))
                .isInstanceOf(ClassNameAlreadyExistsException.class);
    }

    @Test
    void update_unchangedInactiveTeacher_isKept() {
        School school = school(1L, true);
        User teacher = user(2L, UserRole.TEACHER, school, false);
        SchoolClass schoolClass = schoolClass(5L, school, teacher, "Old", true);
        when(classRepository.findById(5L)).thenReturn(Optional.of(schoolClass));
        when(classRepository.existsBySchoolIdAndNameIgnoreCaseAndIdNot(1L, "New", 5L)).thenReturn(false);
        when(classRepository.saveAndFlush(any(SchoolClass.class))).thenAnswer(inv -> inv.getArgument(0));

        ClassResponse response = service.update(5L, new UpdateClassRequest("New", 2L), ADMIN_ID);

        assertThat(response.name()).isEqualTo("New");
        assertThat(response.teacherId()).isEqualTo(2L);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void update_otherTeacher_isValidatedAndSet() {
        School school = school(1L, true);
        SchoolClass schoolClass = schoolClass(5L, school, user(2L, UserRole.TEACHER, school, true), "3.A", true);
        User newTeacher = user(3L, UserRole.TEACHER, school, true);
        when(classRepository.findById(5L)).thenReturn(Optional.of(schoolClass));
        when(classRepository.existsBySchoolIdAndNameIgnoreCaseAndIdNot(1L, "3.A", 5L)).thenReturn(false);
        when(userRepository.findById(3L)).thenReturn(Optional.of(newTeacher));
        when(classRepository.saveAndFlush(any(SchoolClass.class))).thenAnswer(inv -> inv.getArgument(0));

        ClassResponse response = service.update(5L, new UpdateClassRequest("3.A", 3L), ADMIN_ID);

        assertThat(response.teacherId()).isEqualTo(3L);
    }

    @Test
    void update_nameTakenByOtherClass_throws() {
        School school = school(1L, true);
        SchoolClass schoolClass = schoolClass(5L, school, user(2L, UserRole.TEACHER, school, true), "3.A", true);
        when(classRepository.findById(5L)).thenReturn(Optional.of(schoolClass));
        when(classRepository.existsBySchoolIdAndNameIgnoreCaseAndIdNot(1L, "3.B", 5L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(5L, new UpdateClassRequest("3.B", 2L), ADMIN_ID))
                .isInstanceOf(ClassNameAlreadyExistsException.class);
        verify(classRepository, never()).saveAndFlush(any(SchoolClass.class));
    }

    @Test
    void update_unknownClass_throws() {
        when(classRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(5L, new UpdateClassRequest("3.A", 2L), ADMIN_ID))
                .isInstanceOf(SchoolClassNotFoundException.class);
    }

    @Test
    void setActive_sameValue_changesNothing() {
        School school = school(1L, true);
        SchoolClass schoolClass = schoolClass(5L, school, user(2L, UserRole.TEACHER, school, true), "3.A", true);
        when(classRepository.findById(5L)).thenReturn(Optional.of(schoolClass));

        service.setActive(5L, true, ADMIN_ID);

        verify(classRepository, never()).saveAndFlush(any(SchoolClass.class));
    }

    @Test
    void setActive_deactivate_savesFlag() {
        School school = school(1L, true);
        SchoolClass schoolClass = schoolClass(5L, school, user(2L, UserRole.TEACHER, school, true), "3.A", true);
        when(classRepository.findById(5L)).thenReturn(Optional.of(schoolClass));
        when(classRepository.saveAndFlush(any(SchoolClass.class))).thenAnswer(inv -> inv.getArgument(0));

        ClassResponse response = service.setActive(5L, false, ADMIN_ID);

        assertThat(response.active()).isFalse();
        verify(classRepository, times(1)).saveAndFlush(schoolClass);
    }

    @Test
    void setActive_activateValidClass_savesFlag() {
        School school = school(1L, true);
        SchoolClass schoolClass = schoolClass(5L, school, user(2L, UserRole.TEACHER, school, true), "3.A", false);
        when(classRepository.findById(5L)).thenReturn(Optional.of(schoolClass));
        when(classRepository.saveAndFlush(any(SchoolClass.class))).thenAnswer(inv -> inv.getArgument(0));

        ClassResponse response = service.setActive(5L, true, ADMIN_ID);

        assertThat(response.active()).isTrue();
    }

    @Test
    void setActive_activateWithInactiveTeacher_throws() {
        School school = school(1L, true);
        SchoolClass schoolClass = schoolClass(5L, school, user(2L, UserRole.TEACHER, school, false), "3.A", false);
        when(classRepository.findById(5L)).thenReturn(Optional.of(schoolClass));

        assertThatThrownBy(() -> service.setActive(5L, true, ADMIN_ID))
                .isInstanceOfSatisfying(InvalidClassDataException.class,
                        ex -> assertThat(ex.getField()).isEqualTo("teacherId"));
        verify(classRepository, never()).saveAndFlush(any(SchoolClass.class));
    }

    @Test
    void setActive_activateInInactiveSchool_throws() {
        School school = school(1L, false);
        SchoolClass schoolClass = schoolClass(5L, school, user(2L, UserRole.TEACHER, school, true), "3.A", false);
        when(classRepository.findById(5L)).thenReturn(Optional.of(schoolClass));

        assertThatThrownBy(() -> service.setActive(5L, true, ADMIN_ID))
                .isInstanceOf(SchoolInactiveException.class);
    }

    @Test
    void regenerateJoinCode_replacesCode() {
        School school = school(1L, true);
        SchoolClass schoolClass = schoolClass(5L, school, user(2L, UserRole.TEACHER, school, true), "3.A", true);
        when(classRepository.findById(5L)).thenReturn(Optional.of(schoolClass));
        when(joinCodeGenerator.generateUnique()).thenReturn("NEWC2345");
        when(classRepository.saveAndFlush(any(SchoolClass.class))).thenAnswer(inv -> inv.getArgument(0));

        ClassResponse response = service.regenerateJoinCode(5L, ADMIN_ID);

        assertThat(response.joinCode()).isEqualTo("NEWC2345");
    }

    private static School school(Long id, boolean active) {
        School school = new School();
        school.setId(id);
        school.setName("School " + id);
        school.setActive(active);
        return school;
    }

    private static User user(Long id, UserRole role, School school, boolean active) {
        User user = new User();
        user.setId(id);
        user.setName("Jano");
        user.setSurname("Novak");
        user.setRole(role);
        user.setSchool(school);
        user.setActive(active);
        return user;
    }

    private static SchoolClass schoolClass(Long id, School school, User teacher, String name, boolean active) {
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(id);
        schoolClass.setSchool(school);
        schoolClass.setTeacher(teacher);
        schoolClass.setName(name);
        schoolClass.setJoinCode("OLDC2345");
        schoolClass.setActive(active);
        return schoolClass;
    }
}