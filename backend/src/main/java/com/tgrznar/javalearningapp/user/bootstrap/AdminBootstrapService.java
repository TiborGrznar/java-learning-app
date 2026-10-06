package com.tgrznar.javalearningapp.user.bootstrap;

import com.tgrznar.javalearningapp.user.User;
import com.tgrznar.javalearningapp.user.UserRepository;
import com.tgrznar.javalearningapp.user.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Creates the first administrator from the app.admin.* settings (ADMIN_EMAIL, ADMIN_PASSWORD).
 * <p>
 * Rules: nothing happens when an administrator already exists (an existing account is never
 * touched, so changing the environment cannot reset a password); a misconfiguration fails
 * the startup instead of being skipped silently. Messages here are for the operator reading
 * the startup log, not for end users, so they are in English.
 * <p>
 * Deliberately not @Transactional: a duplicate-key error caught inside a transaction would
 * mark it rollback-only. The repository calls run in their own transactions instead.
 */
@Service
public class AdminBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);

    static final int MIN_PASSWORD_LENGTH = 12;
    // BCrypt ignores everything beyond 72 bytes (bytes, not characters).
    static final int MAX_PASSWORD_BYTES = 72;
    private static final int MAX_EMAIL_LENGTH = 255;
    private static final int MAX_NAME_LENGTH = 100;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties properties;

    public AdminBootstrapService(UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,
                                 AdminProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    /**
     * @return true if an administrator was created, false if nothing had to be done
     * @throws IllegalStateException when the configuration is present but unusable
     */
    public boolean createFirstAdminIfMissing() {
        if (userRepository.existsByRole(UserRole.ADMIN)) {
            return false;
        }

        boolean hasEmail = StringUtils.hasText(properties.email());
        boolean hasPassword = StringUtils.hasText(properties.password());

        if (!hasEmail && !hasPassword) {
            log.warn("No administrator exists and ADMIN_EMAIL / ADMIN_PASSWORD are not set. "
                    + "Set both and restart to create the first administrator.");
            return false;
        }
        if (!hasEmail || !hasPassword) {
            throw new IllegalStateException("ADMIN_EMAIL and ADMIN_PASSWORD must be set together, but only one is set");
        }

        String email = properties.email().trim().toLowerCase(Locale.ROOT);
        validate(email, properties.password(), properties.name(), properties.surname());

        // An existing non-admin account is never promoted silently.
        if (userRepository.existsByEmail(email)) {
            throw new IllegalStateException("An account with the e-mail from ADMIN_EMAIL already exists "
                    + "and is not an administrator. Use a different e-mail.");
        }

        User admin = new User();
        admin.setName(properties.name());
        admin.setSurname(properties.surname());
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(properties.password()));
        admin.setRole(UserRole.ADMIN);

        try {
            userRepository.saveAndFlush(admin);
        } catch (DataIntegrityViolationException e) {
            // Benign only if another instance created the administrator between our check and insert.
            if (userRepository.existsByRole(UserRole.ADMIN)) {
                log.info("The administrator account was created concurrently, nothing to do");
                return false;
            }
            throw e;
        }

        log.info("Created the first administrator account {}", email);
        return true;
    }

    /** Never put the password into a message. */
    private void validate(String email, String password, String name, String surname) {
        if (email.length() > MAX_EMAIL_LENGTH || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalStateException("ADMIN_EMAIL is not a valid e-mail address");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("ADMIN_PASSWORD must have at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new IllegalStateException("ADMIN_PASSWORD must have at most " + MAX_PASSWORD_BYTES
                    + " bytes (BCrypt limit), characters with diacritics take more than one byte");
        }
        if (name.length() > MAX_NAME_LENGTH || surname.length() > MAX_NAME_LENGTH) {
            throw new IllegalStateException("ADMIN_NAME and ADMIN_SURNAME must have at most "
                    + MAX_NAME_LENGTH + " characters");
        }
    }
}