package com.tgrznar.javalearningapp.user.admin.api;

import com.jayway.jsonpath.JsonPath;
import com.tgrznar.javalearningapp.auth.security.JwtService;
import com.tgrznar.javalearningapp.testsupport.TestUsers;
import com.tgrznar.javalearningapp.testsupport.TestcontainersConfiguration;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.user.model.UserRole;
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

import java.util.Locale;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A user who is locked out by failed logins or by failed password changes can use the temporary password
 * at once after an administrator reset. The limits are lowered to 3 here. The counters of other users stay.
 * Only a test with real limiters can catch a wrong key, so the keys are checked here and not only by mocks.
 */
@SpringBootTest(properties = {
        "app.rate-limit.login-failures-per-email.max=3",
        "app.rate-limit.password-change-failures-per-user.max=3"})
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
class AdminUserPasswordResetRateLimitIntegrationTest {

    private static final String OLD_PASSWORD = "stare-heslo-1";
    private static final String WRONG = "zle-heslo-9";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestUsers testUsers;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void userLockedOutByFailedLogins_canLogInWithTheTemporaryPasswordAfterTheReset() throws Exception {
        User target = savedUser();
        lockOutLogin(target);
        login(target.getEmail(), OLD_PASSWORD).andExpect(status().isTooManyRequests());

        String temporaryPassword = reset(target);

        login(target.getEmail(), temporaryPassword).andExpect(status().isOk());
    }

    @Test
    void userLockedOutByFailedPasswordChanges_canChangeThePasswordAfterTheReset() throws Exception {
        User target = savedUser();
        for (int i = 0; i < 3; i++) {
            change(target, WRONG, "nove-heslo-22").andExpect(status().isBadRequest());
        }
        change(target, OLD_PASSWORD, "nove-heslo-22").andExpect(status().isTooManyRequests());

        String temporaryPassword = reset(target);

        change(target, temporaryPassword, "nove-heslo-22").andExpect(status().isOk());
    }

    @Test
    void resetDoesNotClearTheCountersOfOtherUsers() throws Exception {
        User target = savedUser();
        User other = savedUser();
        lockOutLogin(other);

        reset(target);

        login(other.getEmail(), OLD_PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    private void lockOutLogin(User user) throws Exception {
        for (int i = 0; i < 3; i++) {
            login(user.getEmail(), WRONG).andExpect(status().isUnauthorized());
        }
    }

    private User savedUser() {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        User user = testUsers.user(UserRole.STUDENT).email(email.toLowerCase(Locale.ROOT)).save();
        user.setPasswordHash(passwordEncoder.encode(OLD_PASSWORD));
        return userRepository.saveAndFlush(user);
    }

    private String reset(User target) throws Exception {
        User admin = testUsers.user(UserRole.ADMIN).email("admin-" + UUID.randomUUID() + "@example.com").save();
        String response = mockMvc.perform(post("/api/v1/users/" + target.getId() + "/password-reset")
                        .header("Authorization", "Bearer " + jwtService.generateAccessToken(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.temporaryPassword");
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions change(User actor, String current, String newPassword) throws Exception {
        return mockMvc.perform(put("/api/v1/users/me/password")
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + newPassword
                        + "\",\"confirmNewPassword\":\"" + newPassword + "\"}"));
    }
}
