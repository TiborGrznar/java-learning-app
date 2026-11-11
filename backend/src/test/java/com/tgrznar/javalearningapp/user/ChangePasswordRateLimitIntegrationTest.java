package com.tgrznar.javalearningapp.user;

import com.tgrznar.javalearningapp.auth.security.JwtService;
import com.tgrznar.javalearningapp.testsupport.TestUsers;
import com.tgrznar.javalearningapp.testsupport.TestcontainersConfiguration;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.user.model.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Wrong current passwords in a password change are limited per user (here to 3), so a stolen access token
 * cannot be used to guess the password. Only failures count, a successful change clears the counter.
 */
@SpringBootTest(properties = "app.rate-limit.password-change-failures-per-user.max=3")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
class ChangePasswordRateLimitIntegrationTest {

    private static final String URL = "/api/v1/users/me/password";
    private static final String CURRENT = "stare-heslo-1";
    private static final String NEW = "nove-heslo-22";
    private static final String WRONG = "zle-heslo-9";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestUsers testUsers;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void afterThreeWrongPasswords_evenTheCorrectOneAnswers429AndNothingChanges() throws Exception {
        User user = savedUser();

        for (int i = 0; i < 3; i++) {
            change(user, WRONG, NEW)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
        }

        change(user, CURRENT, NEW)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

        entityManager.flush();
        entityManager.clear();
        String hash = userRepository.findById(user.getId()).orElseThrow().getPasswordHash();
        assertThat(passwordEncoder.matches(CURRENT, hash)).isTrue();
    }

    @Test
    void theLimitIsPerUser() throws Exception {
        User blocked = savedUser();
        User other = savedUser();
        for (int i = 0; i < 3; i++) {
            change(blocked, WRONG, NEW).andExpect(status().isBadRequest());
        }
        change(blocked, WRONG, NEW).andExpect(status().isTooManyRequests());

        change(other, WRONG, NEW)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
    }

    @Test
    void aSuccessfulChangeClearsTheCounter() throws Exception {
        User user = savedUser();
        change(user, WRONG, NEW).andExpect(status().isBadRequest());
        change(user, WRONG, NEW).andExpect(status().isBadRequest());

        change(user, CURRENT, NEW).andExpect(status().isOk());

        // Without the clearing the third failure below would already be refused, so all three are allowed.
        for (int i = 0; i < 3; i++) {
            change(user, WRONG, "ine-nove-heslo-3").andExpect(status().isBadRequest());
        }
        change(user, WRONG, "ine-nove-heslo-3").andExpect(status().isTooManyRequests());
    }

    private User savedUser() {
        User user = testUsers.user(UserRole.STUDENT).save();
        user.setPasswordHash(passwordEncoder.encode(CURRENT));
        return userRepository.saveAndFlush(user);
    }

    private ResultActions change(User actor, String current, String newPassword) throws Exception {
        return mockMvc.perform(put(URL)
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword":"%s","newPassword":"%s","confirmNewPassword":"%s"}
                        """.formatted(current, newPassword, newPassword)));
    }
}
