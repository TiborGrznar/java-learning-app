package com.tgrznar.javalearningapp.user.admin;

import com.tgrznar.javalearningapp.auth.exception.EmailAlreadyExistsException;
import com.tgrznar.javalearningapp.auth.token.RefreshTokenService;
import com.tgrznar.javalearningapp.common.PageResponse;
import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.school.exception.SchoolInactiveException;
import com.tgrznar.javalearningapp.school.exception.SchoolNotFoundException;
import com.tgrznar.javalearningapp.school.SchoolRepository;
import com.tgrznar.javalearningapp.user.dto.*;
import com.tgrznar.javalearningapp.user.exception.InvalidUserDataException;
import com.tgrznar.javalearningapp.user.exception.LastAdminException;
import com.tgrznar.javalearningapp.user.exception.SelfModificationException;
import com.tgrznar.javalearningapp.user.exception.UserNotFoundException;
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

    /** Replaces name, surname, e-mail and school. The role and the active flag have their own operations. */
    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request, Long adminId) {
        User user = findOrThrow(id);

        School school = resolveSchool(user, request.schoolId());
        if (user.getRole() == UserRole.TEACHER && school == null) {
            throw new InvalidUserDataException("schoolId", "Učiteľ musí mať priradenú školu");
        }

        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailAndIdNot(email, id)) {
            throw new EmailAlreadyExistsException();
        }

        user.setName(request.name().trim());
        user.setSurname(request.surname().trim());
        user.setEmail(email);
        user.setSchool(school);

        User saved = saveUniqueEmail(user);
        log.info("User updated: id={}, updatedByAdminId={}", saved.getId(), adminId);
        return UserResponse.from(saved);
    }

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
        }

        user.setActive(active);
        User saved = userRepository.saveAndFlush(user);
        if (!active) {
            refreshTokenService.revokeAllForUser(saved.getId());
        }

        log.info("User active flag changed: id={}, active={}, changedByAdminId={}", saved.getId(), active, adminId);
        return UserResponse.from(saved);
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