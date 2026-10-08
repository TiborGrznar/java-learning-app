package com.tgrznar.javalearningapp.user.admin;

import com.tgrznar.javalearningapp.auth.exception.EmailAlreadyExistsException;
import com.tgrznar.javalearningapp.auth.token.RefreshTokenService;
import com.tgrznar.javalearningapp.common.PageResponse;
import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.school.SchoolRepository;
import com.tgrznar.javalearningapp.school.exception.SchoolInactiveException;
import com.tgrznar.javalearningapp.school.exception.SchoolNotFoundException;
import com.tgrznar.javalearningapp.user.dto.ChangeRoleRequest;
import com.tgrznar.javalearningapp.user.dto.CreateUserRequest;
import com.tgrznar.javalearningapp.user.dto.CreatedUserResponse;
import com.tgrznar.javalearningapp.user.dto.UpdateUserRequest;
import com.tgrznar.javalearningapp.user.dto.UserResponse;
import com.tgrznar.javalearningapp.user.exception.InvalidUserDataException;
import com.tgrznar.javalearningapp.user.exception.LastAdminException;
import com.tgrznar.javalearningapp.user.exception.SelfModificationException;
import com.tgrznar.javalearningapp.user.exception.UserNotFoundException;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

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

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AdminUserService service;

    // ---------- createUser ----------

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

    // ---------- list ----------

    @Test
    void list_mapsRepositoryPageToResponse() {
        School school = school(5L, true);
        Page<User> page = new PageImpl<>(
                List.of(user(1L, UserRole.STUDENT, true, school), user(2L, UserRole.STUDENT, true, school)),
                PageRequest.of(0, 20), 2);
        when(userRepository.search(eq(5L), eq(UserRole.STUDENT), eq(true), any(Pageable.class))).thenReturn(page);

        PageResponse<UserResponse> response = service.list(5L, UserRole.STUDENT, true, 0, 20);

        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).schoolId()).isEqualTo(5L);
        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(20);
    }

    @Test
    void list_correctsNegativePageAndTooLargeSize() {
        when(userRepository.search(any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        service.list(null, null, null, -5, 1000);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).search(isNull(), isNull(), isNull(), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(AdminUserService.MAX_PAGE_SIZE);
    }

    @Test
    void list_nonPositiveSizeFallsBackToDefault() {
        when(userRepository.search(any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        service.list(null, null, null, 0, 0);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).search(isNull(), isNull(), isNull(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(AdminUserService.DEFAULT_PAGE_SIZE);
    }

    @Test
    void list_sortsBySurnameNameAndId() {
        when(userRepository.search(any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        service.list(null, null, null, 0, 10);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).search(isNull(), isNull(), isNull(), captor.capture());
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by("surname", "name", "id"));
    }

    // ---------- get ----------

    @Test
    void get_returnsUser() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.STUDENT, true, null)));

        UserResponse response = service.get(10L);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.role()).isEqualTo(UserRole.STUDENT);
    }

    @Test
    void get_unknownUser_throwsNotFound() {
        when(userRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(10L)).isInstanceOf(UserNotFoundException.class);
    }

    // ---------- update ----------

    @Test
    void update_changesFieldsAndNormalizesEmail() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.STUDENT, true, null)));
        when(userRepository.existsByEmailAndIdNot("peter@example.com", 10L)).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = service.update(10L,
                new UpdateUserRequest(" Peter ", " Horvath ", "Peter@Example.COM", null), ADMIN_ID);

        assertThat(response.name()).isEqualTo("Peter");
        assertThat(response.surname()).isEqualTo("Horvath");
        assertThat(response.email()).isEqualTo("peter@example.com");
        assertThat(response.role()).isEqualTo(UserRole.STUDENT);
    }

    @Test
    void update_duplicateEmail_throwsAndDoesNotSave() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.STUDENT, true, null)));
        when(userRepository.existsByEmailAndIdNot("taken@example.com", 10L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(10L,
                new UpdateUserRequest("Peter", "Horvath", "taken@example.com", null), ADMIN_ID))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_keepsCurrentSchoolEvenWhenItWasDeactivated() {
        School inactive = school(5L, false);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.TEACHER, true, inactive)));
        when(userRepository.existsByEmailAndIdNot("new@example.com", 10L)).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = service.update(10L,
                new UpdateUserRequest("Jana", "Novakova", "new@example.com", 5L), ADMIN_ID);

        assertThat(response.schoolId()).isEqualTo(5L);
        verifyNoInteractions(schoolRepository);
    }

    @Test
    void update_teacherWithoutSchool_isRejected() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.TEACHER, true, school(5L, true))));

        assertThatThrownBy(() -> service.update(10L,
                new UpdateUserRequest("Jana", "Novakova", "jana@example.com", null), ADMIN_ID))
                .isInstanceOf(InvalidUserDataException.class)
                .extracting(e -> ((InvalidUserDataException) e).getField())
                .isEqualTo("schoolId");

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_toInactiveSchool_isRejected() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.STUDENT, true, null)));
        when(schoolRepository.findById(7L)).thenReturn(Optional.of(school(7L, false)));

        assertThatThrownBy(() -> service.update(10L,
                new UpdateUserRequest("Peter", "Horvath", "peter@example.com", 7L), ADMIN_ID))
                .isInstanceOf(SchoolInactiveException.class);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_unknownUser_throwsNotFound() {
        when(userRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(10L,
                new UpdateUserRequest("Peter", "Horvath", "peter@example.com", null), ADMIN_ID))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void update_uniqueIndexViolation_isTranslatedToEmailAlreadyExists() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.STUDENT, true, null)));
        when(userRepository.existsByEmailAndIdNot("peter@example.com", 10L)).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.update(10L,
                new UpdateUserRequest("Peter", "Horvath", "peter@example.com", null), ADMIN_ID))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    // ---------- changeRole ----------

    @Test
    void changeRole_studentToTeacher_assignsSchool() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.STUDENT, true, null)));
        when(schoolRepository.findById(5L)).thenReturn(Optional.of(school(5L, true)));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = service.changeRole(10L, new ChangeRoleRequest(UserRole.TEACHER, 5L), ADMIN_ID);

        assertThat(response.role()).isEqualTo(UserRole.TEACHER);
        assertThat(response.schoolId()).isEqualTo(5L);
    }

    @Test
    void changeRole_sameRole_isNoOp() {
        when(userRepository.findById(10L))
                .thenReturn(Optional.of(user(10L, UserRole.TEACHER, true, school(5L, true))));

        UserResponse response = service.changeRole(10L, new ChangeRoleRequest(UserRole.TEACHER, null), ADMIN_ID);

        assertThat(response.role()).isEqualTo(UserRole.TEACHER);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void changeRole_ownAccount_isRejected() {
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(user(ADMIN_ID, UserRole.ADMIN, true, null)));

        assertThatThrownBy(() -> service.changeRole(ADMIN_ID, new ChangeRoleRequest(UserRole.STUDENT, null), ADMIN_ID))
                .isInstanceOf(SelfModificationException.class);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void changeRole_lastActiveAdmin_isRejected() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.ADMIN, true, null)));
        when(userRepository.countByRoleAndActive(UserRole.ADMIN, true)).thenReturn(1L);

        assertThatThrownBy(() -> service.changeRole(10L, new ChangeRoleRequest(UserRole.STUDENT, null), ADMIN_ID))
                .isInstanceOf(LastAdminException.class);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void changeRole_adminDemoted_whenAnotherActiveAdminExists() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.ADMIN, true, null)));
        when(userRepository.countByRoleAndActive(UserRole.ADMIN, true)).thenReturn(2L);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = service.changeRole(10L, new ChangeRoleRequest(UserRole.STUDENT, null), ADMIN_ID);

        assertThat(response.role()).isEqualTo(UserRole.STUDENT);
    }

    @Test
    void changeRole_inactiveAdminDemoted_doesNotCountAdmins() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.ADMIN, false, null)));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = service.changeRole(10L, new ChangeRoleRequest(UserRole.STUDENT, null), ADMIN_ID);

        assertThat(response.role()).isEqualTo(UserRole.STUDENT);
        verify(userRepository, never()).countByRoleAndActive(any(), anyBoolean());
    }

    @Test
    void changeRole_toTeacherWithoutSchool_isRejected() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.STUDENT, true, null)));

        assertThatThrownBy(() -> service.changeRole(10L, new ChangeRoleRequest(UserRole.TEACHER, null), ADMIN_ID))
                .isInstanceOf(InvalidUserDataException.class)
                .extracting(e -> ((InvalidUserDataException) e).getField())
                .isEqualTo("schoolId");

        verify(userRepository, never()).saveAndFlush(any());
    }

    // ---------- setActive ----------

    @Test
    void setActive_deactivate_revokesAllRefreshTokens() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.STUDENT, true, null)));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = service.setActive(10L, false, ADMIN_ID);

        assertThat(response.active()).isFalse();
        verify(refreshTokenService).revokeAllForUser(10L);
    }

    @Test
    void setActive_reactivate_doesNotRevokeTokens() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.STUDENT, false, null)));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = service.setActive(10L, true, ADMIN_ID);

        assertThat(response.active()).isTrue();
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void setActive_alreadyInRequestedState_isNoOp() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.STUDENT, false, null)));

        UserResponse response = service.setActive(10L, false, ADMIN_ID);

        assertThat(response.active()).isFalse();
        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void setActive_ownAccount_isRejected() {
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(user(ADMIN_ID, UserRole.ADMIN, true, null)));

        assertThatThrownBy(() -> service.setActive(ADMIN_ID, false, ADMIN_ID))
                .isInstanceOf(SelfModificationException.class);

        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void setActive_lastActiveAdmin_isRejected() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.ADMIN, true, null)));
        when(userRepository.countByRoleAndActive(UserRole.ADMIN, true)).thenReturn(1L);

        assertThatThrownBy(() -> service.setActive(10L, false, ADMIN_ID))
                .isInstanceOf(LastAdminException.class);

        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void setActive_adminDeactivated_whenAnotherActiveAdminExists() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, UserRole.ADMIN, true, null)));
        when(userRepository.countByRoleAndActive(UserRole.ADMIN, true)).thenReturn(2L);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = service.setActive(10L, false, ADMIN_ID);

        assertThat(response.active()).isFalse();
        verify(refreshTokenService).revokeAllForUser(10L);
    }

    // ---------- helpers ----------

    private static User user(Long id, UserRole role, boolean active, School school) {
        User u = new User();
        u.setId(id);
        u.setName("Jano");
        u.setSurname("Novak");
        u.setEmail("jano" + id + "@example.com");
        u.setRole(role);
        u.setActive(active);
        u.setSchool(school);
        return u;
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