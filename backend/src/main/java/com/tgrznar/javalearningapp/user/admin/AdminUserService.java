package com.tgrznar.javalearningapp.user.admin;

import com.tgrznar.javalearningapp.auth.exception.EmailAlreadyExistsException;
import com.tgrznar.javalearningapp.auth.token.RefreshTokenService;
import com.tgrznar.javalearningapp.common.PageResponse;
import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.school.exception.SchoolInactiveException;
import com.tgrznar.javalearningapp.school.exception.SchoolNotFoundException;
import com.tgrznar.javalearningapp.school.SchoolRepository;
import com.tgrznar.javalearningapp.schoolclass.SchoolClassRepository;
import com.tgrznar.javalearningapp.user.dto.*;
import com.tgrznar.javalearningapp.user.exception.InvalidUserDataException;
import com.tgrznar.javalearningapp.user.exception.LastAdminException;
import com.tgrznar.javalearningapp.user.exception.SelfModificationException;
import com.tgrznar.javalearningapp.user.exception.TeacherLeadsClassesException;
import com.tgrznar.javalearningapp.user.exception.UserNotFoundException;
import com.tgrznar.javalearningapp.user.exception.UserNotDeletableException;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.user.model.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/** User management by an administrator (UC-04). */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final SchoolRepository schoolRepository;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator passwordGenerator;
    private final RefreshTokenService refreshTokenService;
    private final SchoolClassRepository classRepository;

    /** Creates a teacher or an administrator with a generated temporary password. */
    @Transactional
    public CreatedUserResponse createUser(CreateUserRequest request, Long adminId) {
        if (request.role() == UserRole.STUDENT) {
            throw new InvalidUserDataException("role",
                    "Študenti sa registrujú sami, administrátor vytvára učiteľov a administrátorov");
        }
        if (request.role() == UserRole.TEACHER && request.schoolId() == null) {
            throw new InvalidUserDataException("schoolId", "Učiteľ musí mať priradenú školu");
        }

        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException();
        }

        School school = null;
        Long schoolId = request.schoolId();
        if (schoolId != null) {
            school = schoolRepository.findById(schoolId)
                    .orElseThrow(() -> new SchoolNotFoundException(schoolId));
            if (!school.isActive()) {
                throw new SchoolInactiveException();
            }
        }

        String temporaryPassword = passwordGenerator.generate();

        User user = new User();
        user.setName(request.name().trim());
        user.setSurname(request.surname().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setRole(request.role());
        user.setSchool(school);

        User saved = saveUniqueEmail(user);

        // Never log the password.
        log.info("User created: id={}, role={}, createdByAdminId={}", saved.getId(), saved.getRole(), adminId);
        return new CreatedUserResponse(UserResponse.from(saved), temporaryPassword);
    }

    /** One page of users. Out-of-range page and size are corrected instead of rejected. */
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(Long schoolId, UserRole role, Boolean active, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by("surname", "name", "id"));

        return PageResponse.from(userRepository.search(schoolId, role, active, pageable).map(UserResponse::from));
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return UserResponse.from(findOrThrow(id));
    }

    /**
     * Replaces name, surname, e-mail and school. The role and the active flag have their own operations.
     * A student whose school changes leaves his class, because the class belongs to the old school.
     */
    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request, Long adminId) {
        User user = findOrThrow(id);

        School school = resolveSchool(user, request.schoolId());
        if (user.getRole() == UserRole.TEACHER && school == null) {
            throw new InvalidUserDataException("schoolId", "Učiteľ musí mať priradenú školu");
        }
        boolean schoolChanges = schoolChanges(user, school);
        if (user.getRole() == UserRole.TEACHER && schoolChanges) {
            ensureLeadsNoActiveClass(user.getId());
        }

        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailAndIdNot(email, id)) {
            throw new EmailAlreadyExistsException();
        }

        user.setName(request.name().trim());
        user.setSurname(request.surname().trim());
        user.setEmail(email);
        if (user.getRole() == UserRole.STUDENT && schoolChanges) {
            user.setSchoolClass(null);
        }
        user.setSchool(school);

        User saved = saveUniqueEmail(user);
        log.info("User updated: id={}, updatedByAdminId={}", saved.getId(), adminId);
        return UserResponse.from(saved);
    }

    /** Changes the role. A student who stops being one leaves his class, because only students belong to a class. */
    @Transactional
    public UserResponse changeRole(Long id, ChangeRoleRequest request, Long adminId) {
        User user = findOrThrow(id);
        if (user.getId().equals(adminId)) {
            throw new SelfModificationException();
        }
        if (user.getRole() == request.role()) {
            return UserResponse.from(user);
        }
        if (user.getRole() == UserRole.ADMIN && user.isActive()) {
            ensureAnotherActiveAdminExists();
        }
        // The requested role differs from the current one here, so a teacher is about to stop being one.
        if (user.getRole() == UserRole.TEACHER) {
            ensureLeadsNoActiveClass(user.getId());
        }

        School school = user.getSchool();
        if (request.role() == UserRole.TEACHER) {
            if (request.schoolId() != null) {
                school = requireActiveSchool(request.schoolId());
            }
            if (school == null) {
                throw new InvalidUserDataException("schoolId", "Učiteľ musí mať priradenú školu");
            }
        }

        UserRole previousRole = user.getRole();
        if (previousRole == UserRole.STUDENT) {
            user.setSchoolClass(null);
        }
        user.setRole(request.role());
        user.setSchool(school);

        User saved = userRepository.saveAndFlush(user);
        log.info("User role changed: id={}, from={}, to={}, changedByAdminId={}",
                saved.getId(), previousRole, saved.getRole(), adminId);
        return UserResponse.from(saved);
    }

    /** Deactivates or reactivates an account. Deactivation also revokes all refresh tokens of the user. */
    @Transactional
    public UserResponse setActive(Long id, boolean active, Long adminId) {
        User user = findOrThrow(id);
        if (user.isActive() == active) {
            return UserResponse.from(user);
        }

        if (!active) {
            if (user.getId().equals(adminId)) {
                throw new SelfModificationException();
            }
            if (user.getRole() == UserRole.ADMIN) {
                ensureAnotherActiveAdminExists();
            }
            if (user.getRole() == UserRole.TEACHER) {
                ensureLeadsNoActiveClass(user.getId());
            }
        }

        user.setActive(active);
        User saved = userRepository.saveAndFlush(user);
        if (!active) {
            refreshTokenService.revokeAllForUser(saved.getId());
        }

        log.info("User active flag changed: id={}, active={}, changedByAdminId={}", saved.getId(), active, adminId);
        return UserResponse.from(saved);
    }

    /**
     * Permanently deletes a student. His refresh tokens, progress and results go with him (cascade in the
     * database). Teachers and administrators are never deleted, they are deactivated. The caller has to repeat
     * the student's e-mail, so a wrong id or a misclick does not destroy data.
     */
    @Transactional
    public void deleteStudent(Long id, String confirmEmail, Long adminId) {
        User user = findOrThrow(id);
        if (user.getRole() != UserRole.STUDENT) {
            throw new UserNotDeletableException(id);
        }
        if (confirmEmail == null || !user.getEmail().equalsIgnoreCase(confirmEmail.trim())) {
            throw new InvalidUserDataException("confirmEmail", "Zadaný e-mail sa nezhoduje s e-mailom študenta");
        }

        userRepository.delete(user);
        // Flush now so a violated foreign key surfaces here, inside the transaction.
        userRepository.flush();
        // The e-mail is personal data, so only ids are logged.
        log.info("Student deleted: id={}, deletedByAdminId={}", id, adminId);
    }

    private User findOrThrow(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    /** Keeps the current school when unchanged (even if it was deactivated since), otherwise requires an active one. */
    private School resolveSchool(User user, Long schoolId) {
        if (schoolId == null) {
            return null;
        }
        School current = user.getSchool();
        if (current != null && current.getId().equals(schoolId)) {
            return current;
        }
        return requireActiveSchool(schoolId);
    }

    /** True when the user would end up in a different school; two missing schools are not a change. */
    private boolean schoolChanges(User user, School newSchool) {
        School current = user.getSchool();
        if (current == null || newSchool == null) {
            return current != newSchool;
        }
        return !current.getId().equals(newSchool.getId());
    }

    private School requireActiveSchool(Long schoolId) {
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new SchoolNotFoundException(schoolId));
        if (!school.isActive()) {
            throw new SchoolInactiveException();
        }
        return school;
    }

    /** Called for an active administrator that is about to stop being one: at least one other must remain. */
    private void ensureAnotherActiveAdminExists() {
        if (userRepository.countByRoleAndActive(UserRole.ADMIN, true) <= 1) {
            throw new LastAdminException();
        }
    }

    /**
     * Called for a teacher that is about to be deactivated, demoted or moved to another school. An active class
     * would be left with a teacher who cannot lead it, so the administrator has to reassign or deactivate it first.
     */
    private void ensureLeadsNoActiveClass(Long teacherId) {
        if (classRepository.existsByTeacherIdAndActive(teacherId, true)) {
            throw new TeacherLeadsClassesException();
        }
    }

    private User saveUniqueEmail(User user) {
        try {
            // Flush now so a concurrent duplicate is caught here, not at commit time.
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyExistsException();
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}