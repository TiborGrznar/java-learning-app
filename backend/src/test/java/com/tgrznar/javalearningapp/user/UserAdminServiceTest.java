package com.tgrznar.javalearningapp.user;

import com.tgrznar.javalearningapp.auth.exception.EmailAlreadyExistsException;
import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.school.SchoolInactiveException;
import com.tgrznar.javalearningapp.school.SchoolNotFoundException;
import com.tgrznar.javalearningapp.school.SchoolRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    private static final Long ADMIN_ID = 99L;
    private static final String TEMP_PASSWORD = "TempPass12345678";

    @Mock
    private UserRepository userRepository;

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TemporaryPasswordGenerator passwordGenerator;

    @InjectMocks
    private UserAdminService service;

    @Test
    void createTeacher_storesHashAndReturnsTemporaryPasswordOnce() {
        when(userRepository.existsByEmail("jana@example.com")).thenReturn(false);
        when(schoolRepository.findById(5L)).thenReturn(Optional.of(school(5L, true)));
        when(passwordGenerator.generate()).thenReturn(TEMP_PASSWORD);
        when(passwordEncoder.encode(TEMP_PASSWORD)).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(10L);
            return u;
        });

        CreatedUserResponse response = service.createUser(
                new CreateUserRequest(" Jana ", " Novakova ", "Jana@Example.COM", UserRole.TEACHER, 5L), ADMIN_ID);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("jana@example.com");
        assertThat(saved.getName()).isEqualTo("Jana");
        assertThat(saved.getSurname()).isEqualTo("Novakova");
        assertThat(saved.getRole()).isEqualTo(UserRole.TEACHER);
        assertThat(saved.getPasswordHash()).isEqualTo("hashed");
        assertThat(saved.getSchool().getId()).isEqualTo(5L);
        assertThat(response.temporaryPassword()).isEqualTo(TEMP_PASSWORD);
        assertThat(response.user().schoolId()).isEqualTo(5L);
        assertThat(response.toString()).doesNotContain(TEMP_PASSWORD);
    }

    @Test
    void createAdmin_withoutSchool_isAllowed() {
        when(userRepository.existsByEmail("boss@example.com")).thenReturn(false);
        when(passwordGenerator.generate()).thenReturn(TEMP_PASSWORD);
        when(passwordEncoder.encode(TEMP_PASSWORD)).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        CreatedUserResponse response = service.createUser(
                new CreateUserRequest("Boss", "Admin", "boss@example.com", UserRole.ADMIN, null), ADMIN_ID);

        assertThat(response.user().role()).isEqualTo(UserRole.ADMIN);
        assertThat(response.user().schoolId()).isNull();
        verifyNoInteractions(schoolRepository);
    }

    @Test
    void createStudent_isRejected() {
        assertThatThrownBy(() -> service.createUser(
                new CreateUserRequest("Jano", "Novak", "jano@example.com", UserRole.STUDENT, null), ADMIN_ID))
                .isInstanceOf(InvalidUserDataException.class)
                .extracting(e -> ((InvalidUserDataException) e).getField())
                .isEqualTo("role");

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void createTeacher_withoutSchool_isRejected() {
        assertThatThrownBy(() -> service.createUser(
                new CreateUserRequest("Jana", "Novakova", "jana@example.com", UserRole.TEACHER, null), ADMIN_ID))
                .isInstanceOf(InvalidUserDataException.class)
                .extracting(e -> ((InvalidUserDataException) e).getField())
                .isEqualTo("schoolId");

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void duplicateEmail_throwsAndDoesNotGeneratePassword() {
        when(userRepository.existsByEmail("jana@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.createUser(
                new CreateUserRequest("Jana", "Novakova", "jana@example.com", UserRole.TEACHER, 5L), ADMIN_ID))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verifyNoInteractions(passwordGenerator);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void unknownSchool_throwsNotFound() {
        when(userRepository.existsByEmail("jana@example.com")).thenReturn(false);
        when(schoolRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createUser(
                new CreateUserRequest("Jana", "Novakova", "jana@example.com", UserRole.TEACHER, 5L), ADMIN_ID))
                .isInstanceOf(SchoolNotFoundException.class);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void inactiveSchool_isRejected() {
        when(userRepository.existsByEmail("jana@example.com")).thenReturn(false);
        when(schoolRepository.findById(5L)).thenReturn(Optional.of(school(5L, false)));

        assertThatThrownBy(() -> service.createUser(
                new CreateUserRequest("Jana", "Novakova", "jana@example.com", UserRole.TEACHER, 5L), ADMIN_ID))
                .isInstanceOf(SchoolInactiveException.class);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void uniqueIndexViolation_isTranslatedToEmailAlreadyExists() {
        when(userRepository.existsByEmail("jana@example.com")).thenReturn(false);
        when(schoolRepository.findById(5L)).thenReturn(Optional.of(school(5L, true)));
        when(passwordGenerator.generate()).thenReturn(TEMP_PASSWORD);
        when(passwordEncoder.encode(TEMP_PASSWORD)).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.createUser(
                new CreateUserRequest("Jana", "Novakova", "jana@example.com", UserRole.TEACHER, 5L), ADMIN_ID))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    private static School school(Long id, boolean active) {
        School s = new School();
        s.setId(id);
        s.setName("School " + id);
        s.setAddress("Address");
        s.setActive(active);
        return s;
    }
}