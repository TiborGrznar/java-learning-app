package com.tgrznar.javalearningapp.user.bootstrap;

import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapServiceTest {

    private static final String EMAIL = "admin@example.com";
    private static final String PASSWORD = "dlhe-heslo-123";

    @Mock
    private UserRepository userRepository;

    // Real encoder with a low cost factor: fast, and verifies real hashing behaviour.
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private AdminBootstrapService service(String email, String password) {
        return service(email, password, null, null);
    }

    private AdminBootstrapService service(String email, String password, String name, String surname) {
        return new AdminBootstrapService(userRepository, passwordEncoder,
                new AdminProperties(email, password, name, surname));
    }

    // Unstubbed repository methods return false, which is the "nothing exists yet" state.

    // ---------- nothing to do ----------

    @Test
    void existingAdmin_isLeftAloneEvenWithInvalidConfiguration() {
        when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(true);

        boolean created = service("not-an-email", "short").createFirstAdminIfMissing();

        assertThat(created).isFalse();
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void noAdminAndNoConfiguration_doesNothing() {
        boolean created = service(null, null).createFirstAdminIfMissing();

        assertThat(created).isFalse();
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void noAdminAndBlankConfiguration_doesNothing() {
        boolean created = service("  ", "").createFirstAdminIfMissing();

        assertThat(created).isFalse();
        verify(userRepository, never()).saveAndFlush(any());
    }

    // ---------- creation ----------

    @Test
    void createsActiveAdminWithHashedPasswordAndNormalizedEmail() {
        boolean created = service("  Admin@Example.COM ", PASSWORD, "Jano", "Novák").createFirstAdminIfMissing();

        assertThat(created).isTrue();
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getSchool()).isNull();
        assertThat(saved.getName()).isEqualTo("Jano");
        assertThat(saved.getSurname()).isEqualTo("Novák");
        assertThat(saved.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, saved.getPasswordHash())).isTrue();
    }

    @Test
    void usesDefaultNamesWhenNotConfigured() {
        service(EMAIL, PASSWORD).createFirstAdminIfMissing();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Admin");
        assertThat(captor.getValue().getSurname()).isEqualTo("Administrátor");
    }

    // ---------- misconfiguration fails the startup ----------

    @Test
    void onlyEmailSet_failsWithoutCreating() {
        assertThatThrownBy(() -> service(EMAIL, null).createFirstAdminIfMissing())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be set together");
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void onlyPasswordSet_failsWithoutCreating() {
        assertThatThrownBy(() -> service(null, PASSWORD).createFirstAdminIfMissing())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be set together");
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void invalidEmail_fails() {
        assertThatThrownBy(() -> service("not-an-email", PASSWORD).createFirstAdminIfMissing())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("valid e-mail");
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void passwordOfElevenCharacters_failsAndMessageDoesNotRevealIt() {
        String password = "abcdefghijk";

        assertThatThrownBy(() -> service(EMAIL, password).createFirstAdminIfMissing())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 12")
                .hasMessageNotContaining(password);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void passwordOfTwelveCharacters_isAccepted() {
        assertThat(service(EMAIL, "abcdefghijkl").createFirstAdminIfMissing()).isTrue();
    }

    @Test
    void passwordOfSeventyTwoBytes_isAccepted() {
        assertThat(service(EMAIL, "a".repeat(72)).createFirstAdminIfMissing()).isTrue();
    }

    @Test
    void passwordOfSeventyThreeBytes_fails() {
        assertThatThrownBy(() -> service(EMAIL, "a".repeat(73)).createFirstAdminIfMissing())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("72 bytes");
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void passwordWithDiacriticsOverSeventyTwoBytes_failsEvenWhenShortInCharacters() {
        // 40 characters, but each "š" takes two bytes in UTF-8, so 80 bytes in total.
        String password = "š".repeat(40);

        assertThatThrownBy(() -> service(EMAIL, password).createFirstAdminIfMissing())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("72 bytes");
    }

    @Test
    void nameLongerThanColumn_fails() {
        assertThatThrownBy(() -> service(EMAIL, PASSWORD, "a".repeat(101), "Novák").createFirstAdminIfMissing())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at most 100");
    }

    @Test
    void existingNonAdminWithSameEmail_failsAndIsNotPromoted() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> service(EMAIL, PASSWORD).createFirstAdminIfMissing())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
        verify(userRepository, never()).saveAndFlush(any());
    }

    // ---------- concurrent startup ----------

    @Test
    void duplicateKeyWhenAnotherInstanceCreatedTheAdmin_isIgnored() {
        // First check: no admin. After the failed insert the other instance's admin is visible.
        when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(false, true);
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThat(service(EMAIL, PASSWORD).createFirstAdminIfMissing()).isFalse();
    }

    @Test
    void integrityViolationWithoutAnyAdmin_isRethrown() {
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("other problem"));

        assertThatThrownBy(() -> service(EMAIL, PASSWORD).createFirstAdminIfMissing())
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}