package com.tgrznar.javalearningapp.user;

import com.tgrznar.javalearningapp.auth.exception.EmailAlreadyExistsException;
import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.school.SchoolInactiveException;
import com.tgrznar.javalearningapp.school.SchoolNotFoundException;
import com.tgrznar.javalearningapp.school.SchoolRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/** User management by an administrator (UC-04). */
@Service
public class UserAdminService {

    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);

    private final UserRepository userRepository;
    private final SchoolRepository schoolRepository;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator passwordGenerator;

    public UserAdminService(UserRepository userRepository,
                            SchoolRepository schoolRepository,
                            PasswordEncoder passwordEncoder,
                            TemporaryPasswordGenerator passwordGenerator) {
        this.userRepository = userRepository;
        this.schoolRepository = schoolRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordGenerator = passwordGenerator;
    }

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

        User saved;
        try {
            // Flush now so a concurrent duplicate is caught here, not at commit time.
            saved = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyExistsException();
        }

        // Never log the password.
        log.info("User created: id={}, role={}, createdByAdminId={}", saved.getId(), saved.getRole(), adminId);
        return new CreatedUserResponse(UserResponse.from(saved), temporaryPassword);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}