package com.tgrznar.javalearningapp.user.bootstrap;

import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests of the first-administrator creation against the real database.
 * The development database may already contain an administrator, so every test first removes
 * all of them inside its own transaction; the rollback at the end restores them.
 */
@SpringBootTest
@Transactional
class AdminBootstrapIntegrationTest {

    private static final String PASSWORD = "dlhe-heslo-123";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void existsByRole_reflectsTheRoleStoredInTheDatabase() {
        removeAllAdmins();
        assertThat(userRepository.existsByRole(UserRole.ADMIN)).isFalse();

        saveUser(uniqueEmail(), UserRole.STUDENT);
        assertThat(userRepository.existsByRole(UserRole.ADMIN)).isFalse();

        saveUser(uniqueEmail(), UserRole.ADMIN);
        assertThat(userRepository.existsByRole(UserRole.ADMIN)).isTrue();
    }

    @Test
    void createsTheFirstAdminInTheDatabase() {
        removeAllAdmins();
        String email = uniqueEmail();

        boolean created = service(email).createFirstAdminIfMissing();

        assertThat(created).isTrue();
        User admin = userRepository.findByEmail(email).orElseThrow();
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.isActive()).isTrue();
        assertThat(admin.getName()).isEqualTo("Test");
        assertThat(admin.getSurname()).isEqualTo("Admin");
        assertThat(passwordEncoder.matches(PASSWORD, admin.getPasswordHash())).isTrue();
    }

    @Test
    void runningAgainDoesNotCreateAnotherAdmin() {
        removeAllAdmins();
        service(uniqueEmail()).createFirstAdminIfMissing();

        String otherEmail = uniqueEmail();
        boolean created = service(otherEmail).createFirstAdminIfMissing();

        assertThat(created).isFalse();
        assertThat(userRepository.existsByEmail(otherEmail)).isFalse();
    }

    @Test
    void existingStudentWithTheSameEmail_isNotPromotedToAdmin() {
        removeAllAdmins();
        String email = uniqueEmail();
        saveUser(email, UserRole.STUDENT);

        assertThatThrownBy(() -> service(email).createFirstAdminIfMissing())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");

        assertThat(userRepository.findByEmail(email).orElseThrow().getRole()).isEqualTo(UserRole.STUDENT);
        assertThat(userRepository.existsByRole(UserRole.ADMIN)).isFalse();
    }

    // ---------- helpers ----------

    private AdminBootstrapService service(String email) {
        return new AdminBootstrapService(userRepository, passwordEncoder,
                new AdminProperties(email, PASSWORD, "Test", "Admin"));
    }

    private void removeAllAdmins() {
        List<User> admins = userRepository.findAll().stream()
                .filter(user -> user.getRole() == UserRole.ADMIN)
                .toList();
        userRepository.deleteAll(admins);
        userRepository.flush();
    }

    private void saveUser(String email, UserRole role) {
        User user = new User();
        user.setName("Jano");
        user.setSurname("Novak");
        user.setEmail(email);
        // Not a valid BCrypt hash: these users never log in.
        user.setPasswordHash("not-a-real-hash");
        user.setRole(role);
        userRepository.saveAndFlush(user);
    }

    private String uniqueEmail() {
        return "boot-" + UUID.randomUUID() + "@example.com";
    }
}