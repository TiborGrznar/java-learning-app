package com.tgrznar.javalearningapp.user.admin.api;

import com.tgrznar.javalearningapp.auth.security.JwtService;
import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.school.SchoolRepository;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/**
 * Shared setup and helpers of the administrator user API integration tests.
 * Every test class extends it, so they all share one Spring context. E-mails and school
 * names are unique per test (UUID) and each test is rolled back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
abstract class AdminUserApiTestSupport {

    protected static final String URL = "/api/v1/users";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected SchoolRepository schoolRepository;

    @Autowired
    protected JwtService jwtService;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    /** Performs the request with a token of a freshly created user of the given role. */
    protected ResultActions as(MockHttpServletRequestBuilder request, UserRole role) throws Exception {
        return asUser(request, saveUser(uniqueEmail().toLowerCase(Locale.ROOT), role));
    }

    protected ResultActions asUser(MockHttpServletRequestBuilder request, User actor) throws Exception {
        String token = jwtService.generateAccessToken(actor);
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    protected User saveUser(String email, UserRole role) {
        return saveUser(email, role, null, "Novak", true);
    }

    protected User saveUser(String email, UserRole role, School school, String surname, boolean active) {
        User user = new User();
        user.setName("Jano");
        user.setSurname(surname);
        user.setEmail(email);
        // Not a valid BCrypt hash: these users never log in, they only get a token.
        user.setPasswordHash("not-a-real-hash");
        user.setRole(role);
        user.setSchool(school);
        user.setActive(active);
        return userRepository.saveAndFlush(user);
    }

    protected School saveSchool(boolean active) {
        School school = new School();
        school.setName("Test school " + UUID.randomUUID());
        school.setAddress("Test street 1");
        school.setActive(active);
        return schoolRepository.saveAndFlush(school);
    }

    /**
     * Makes the target the only active administrator in the database (rolled back after the test).
     * The other administrators are deactivated, which works because access tokens are stateless
     * and is_active is not checked per request (known gap in open-decisions.md). When that check
     * is added, this helper has to be rewritten.
     */
    protected void makeOnlyActiveAdmin(User target) {
        userRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.ADMIN && u.isActive() && !u.getId().equals(target.getId()))
                .forEach(u -> u.setActive(false));
        userRepository.flush();
    }

    protected static String url(Long id) {
        return URL + "/" + id;
    }

    protected static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}