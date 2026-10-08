package com.tgrznar.javalearningapp.security;

import com.tgrznar.javalearningapp.auth.security.JwtService;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests of role checks (@PreAuthorize) and of the JSON bodies for 401 and 403, using the
 * test-only endpoints in RoleTestController. Real tokens go through the whole filter chain.
 * Each test runs in a transaction that is rolled back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoleAuthorizationIntegrationTest {

    private static final String ADMIN_URL = "/api/v1/test-roles/admin";
    private static final String TEACHER_OR_ADMIN_URL = "/api/v1/test-roles/teacher-or-admin";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    // ---------- 401: no usable token ----------

    @Test
    void withoutToken_returns401WithJsonBody() throws Exception {
        mockMvc.perform(get(ADMIN_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void withGarbageToken_returns401WithJsonBody() throws Exception {
        mockMvc.perform(get(ADMIN_URL).header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void withTamperedToken_returns401WithSameBody() throws Exception {
        User admin = saveUser(UserRole.ADMIN);

        mockMvc.perform(get(ADMIN_URL).header("Authorization", bearer(admin) + "x"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // ---------- hasRole('ADMIN') ----------

    @Test
    void adminEndpoint_asStudent_returns403() throws Exception {
        getAs(ADMIN_URL, saveUser(UserRole.STUDENT))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void adminEndpoint_asTeacher_returns403() throws Exception {
        getAs(ADMIN_URL, saveUser(UserRole.TEACHER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void adminEndpoint_asAdmin_returns200() throws Exception {
        getAs(ADMIN_URL, saveUser(UserRole.ADMIN))
                .andExpect(status().isOk())
                .andExpect(content().string("admin"));
    }

    @Test
    void forbiddenResponse_doesNotRevealRequiredRole() throws Exception {
        getAs(ADMIN_URL, saveUser(UserRole.STUDENT))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString("ADMIN"))))
                .andExpect(content().string(not(containsString("ROLE_"))));
    }

    // ---------- hasAnyRole('TEACHER', 'ADMIN') ----------

    @Test
    void teacherOrAdminEndpoint_asStudent_returns403() throws Exception {
        getAs(TEACHER_OR_ADMIN_URL, saveUser(UserRole.STUDENT))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void teacherOrAdminEndpoint_asTeacher_returns200() throws Exception {
        getAs(TEACHER_OR_ADMIN_URL, saveUser(UserRole.TEACHER))
                .andExpect(status().isOk());
    }

    @Test
    void teacherOrAdminEndpoint_asAdmin_returns200() throws Exception {
        getAs(TEACHER_OR_ADMIN_URL, saveUser(UserRole.ADMIN))
                .andExpect(status().isOk());
    }

    // ---------- helpers ----------

    private ResultActions getAs(String url, User user) throws Exception {
        return mockMvc.perform(get(url).header("Authorization", bearer(user)));
    }

    private User saveUser(UserRole role) {
        User user = new User();
        user.setName("Jano");
        user.setSurname("Novak");
        user.setEmail("user-" + UUID.randomUUID() + "@example.com");
        // Not a valid BCrypt hash: these users never log in, they only get a token.
        user.setPasswordHash("not-a-real-hash");
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateAccessToken(user);
    }
}