package com.tgrznar.javalearningapp.user;

import com.tgrznar.javalearningapp.auth.security.JwtService;
import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.school.SchoolRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests of GET /api/v1/users/me against the real database. Users are created directly
 * through the repository and authenticated with a real token from JwtService, so the
 * request passes through the whole security filter chain.
 * Each test runs in a transaction that is rolled back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserApiIntegrationTest {

    private static final String ME_URL = "/api/v1/users/me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    void me_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(ME_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withGarbageToken_returns401() throws Exception {
        mockMvc.perform(get(ME_URL).header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_returnsProfileOfTokenOwner() throws Exception {
        User user = saveUser(UserRole.STUDENT, null, true);

        mockMvc.perform(get(ME_URL).header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.name").value("Jano"))
                .andExpect(jsonPath("$.surname").value("Novak"))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.schoolId").isEmpty())
                .andExpect(jsonPath("$.schoolName").isEmpty());
    }

    @Test
    void me_neverExposesPasswordHash() throws Exception {
        User user = saveUser(UserRole.STUDENT, null, true);

        mockMvc.perform(get(ME_URL).header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void me_includesSchoolOfTeacher() throws Exception {
        School school = new School();
        school.setName("Gymnazium Test");
        school.setAddress("Hlavna 1, Banska Bystrica");
        schoolRepository.save(school);
        User teacher = saveUser(UserRole.TEACHER, school, true);

        mockMvc.perform(get(ME_URL).header("Authorization", bearer(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andExpect(jsonPath("$.schoolId").value(school.getId()))
                .andExpect(jsonPath("$.schoolName").value("Gymnazium Test"));
    }

    @Test
    void me_withDeactivatedAccount_returns403() throws Exception {
        User user = saveUser(UserRole.STUDENT, null, false);

        mockMvc.perform(get(ME_URL).header("Authorization", bearer(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void me_whenUserNoLongerExists_returns401InvalidToken() throws Exception {
        User user = saveUser(UserRole.STUDENT, null, true);
        String token = bearer(user);
        userRepository.delete(user);
        userRepository.flush();

        mockMvc.perform(get(ME_URL).header("Authorization", token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    // ---------- helpers ----------

    private User saveUser(UserRole role, School school, boolean active) {
        User user = new User();
        user.setName("Jano");
        user.setSurname("Novak");
        user.setEmail("user-" + UUID.randomUUID() + "@example.com");
        // Not a valid BCrypt hash: these users never log in, they only get a token.
        user.setPasswordHash("not-a-real-hash");
        user.setRole(role);
        user.setSchool(school);
        user.setActive(active);
        return userRepository.saveAndFlush(user);
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateAccessToken(user);
    }
}