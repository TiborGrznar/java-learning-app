package com.tgrznar.javalearningapp.user.admin;

import com.jayway.jsonpath.JsonPath;
import com.tgrznar.javalearningapp.auth.security.JwtService;
import com.tgrznar.javalearningapp.school.School;
import com.tgrznar.javalearningapp.school.SchoolRepository;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests of POST /api/v1/users (administrator creates a teacher or an administrator).
 * E-mails and school names are unique per test (UUID). Each test is rolled back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminUserApiIntegrationTest {

    private static final String URL = "/api/v1/users";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ---------- 401 / 403 ----------

    @Test
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json(uniqueEmail(), "ADMIN", null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void asStudent_returns403() throws Exception {
        as(post(URL).contentType(MediaType.APPLICATION_JSON).content(json(uniqueEmail(), "ADMIN", null)),
                UserRole.STUDENT)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void asTeacher_returns403AndCreatesNothing() throws Exception {
        String email = uniqueEmail();

        as(post(URL).contentType(MediaType.APPLICATION_JSON).content(json(email, "ADMIN", null)), UserRole.TEACHER)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(userRepository.existsByEmail(email.toLowerCase(Locale.ROOT))).isFalse();
    }

    // ---------- successful creation ----------

    @Test
    void asAdmin_createsTeacher_returns201WithOneTimePassword() throws Exception {
        School school = saveSchool(true);
        String email = uniqueEmail();

        as(post(URL).contentType(MediaType.APPLICATION_JSON).content(json(email, "TEACHER", school.getId())),
                UserRole.ADMIN)
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.temporaryPassword").value(matchesPattern("[A-Za-z0-9]{16}")))
                .andExpect(jsonPath("$.user.id").isNumber())
                .andExpect(jsonPath("$.user.role").value("TEACHER"))
                .andExpect(jsonPath("$.user.schoolId").value(school.getId()))
                .andExpect(jsonPath("$.user.active").value(true))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void createdTeacher_hasHashedPasswordInDatabase() throws Exception {
        School school = saveSchool(true);
        String email = uniqueEmail();

        MvcResult result = as(post(URL).contentType(MediaType.APPLICATION_JSON)
                .content(json(email, "TEACHER", school.getId())), UserRole.ADMIN)
                .andExpect(status().isCreated())
                .andReturn();
        String temporaryPassword = JsonPath.read(result.getResponse().getContentAsString(), "$.temporaryPassword");

        User saved = userRepository.findByEmail(email.toLowerCase(Locale.ROOT)).orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo(temporaryPassword);
        assertThat(passwordEncoder.matches(temporaryPassword, saved.getPasswordHash())).isTrue();
    }

    @Test
    void createdTeacher_canLogInWithTheTemporaryPassword() throws Exception {
        School school = saveSchool(true);
        String email = uniqueEmail();

        MvcResult result = as(post(URL).contentType(MediaType.APPLICATION_JSON)
                .content(json(email, "TEACHER", school.getId())), UserRole.ADMIN)
                .andExpect(status().isCreated())
                .andReturn();
        String temporaryPassword = JsonPath.read(result.getResponse().getContentAsString(), "$.temporaryPassword");

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + temporaryPassword + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void asAdmin_createsAdminWithoutSchool_returns201() throws Exception {
        as(post(URL).contentType(MediaType.APPLICATION_JSON).content(json(uniqueEmail(), "ADMIN", null)),
                UserRole.ADMIN)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.user.schoolId").isEmpty());
    }

    @Test
    void email_isStoredLowerCase() throws Exception {
        String email = "Teacher-" + UUID.randomUUID() + "@Example.COM";

        as(post(URL).contentType(MediaType.APPLICATION_JSON).content(json(email, "ADMIN", null)), UserRole.ADMIN)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value(email.toLowerCase(Locale.ROOT)));
    }

    // ---------- rejected requests ----------

    @Test
    void duplicateEmailInDifferentCase_returns409() throws Exception {
        String email = uniqueEmail();
        saveUser(email.toLowerCase(Locale.ROOT), UserRole.STUDENT);

        as(post(URL).contentType(MediaType.APPLICATION_JSON).content(json(email.toUpperCase(Locale.ROOT), "ADMIN", null)),
                UserRole.ADMIN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void studentRole_returns400WithFieldError() throws Exception {
        as(post(URL).contentType(MediaType.APPLICATION_JSON).content(json(uniqueEmail(), "STUDENT", null)),
                UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.role").exists());
    }

    @Test
    void teacherWithoutSchool_returns400WithFieldError() throws Exception {
        as(post(URL).contentType(MediaType.APPLICATION_JSON).content(json(uniqueEmail(), "TEACHER", null)),
                UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.schoolId").exists());
    }

    @Test
    void unknownSchool_returns404() throws Exception {
        as(post(URL).contentType(MediaType.APPLICATION_JSON).content(json(uniqueEmail(), "TEACHER", 999999999L)),
                UserRole.ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCHOOL_NOT_FOUND"));
    }

    @Test
    void inactiveSchool_returns409() throws Exception {
        School school = saveSchool(false);

        as(post(URL).contentType(MediaType.APPLICATION_JSON).content(json(uniqueEmail(), "TEACHER", school.getId())),
                UserRole.ADMIN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHOOL_INACTIVE"));
    }

    @Test
    void missingRole_returns400WithFieldError() throws Exception {
        String body = "{\"name\":\"Jana\",\"surname\":\"Novakova\",\"email\":\"" + uniqueEmail() + "\"}";

        as(post(URL).contentType(MediaType.APPLICATION_JSON).content(body), UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.role").exists());
    }

    @Test
    void unknownRoleValue_returns400InvalidBody() throws Exception {
        as(post(URL).contentType(MediaType.APPLICATION_JSON).content(json(uniqueEmail(), "SUPERHERO", null)),
                UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
    }

    @Test
    void invalidEmail_returns400WithFieldError() throws Exception {
        as(post(URL).contentType(MediaType.APPLICATION_JSON).content(json("not-an-email", "ADMIN", null)),
                UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void blankName_returns400WithFieldError() throws Exception {
        String body = "{\"name\":\"  \",\"surname\":\"Novakova\",\"email\":\"" + uniqueEmail()
                + "\",\"role\":\"ADMIN\"}";

        as(post(URL).contentType(MediaType.APPLICATION_JSON).content(body), UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    // ---------- helpers ----------

    private ResultActions as(MockHttpServletRequestBuilder request, UserRole role) throws Exception {
        String token = jwtService.generateAccessToken(saveUser(uniqueEmail().toLowerCase(Locale.ROOT), role));
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private User saveUser(String email, UserRole role) {
        User user = new User();
        user.setName("Jano");
        user.setSurname("Novak");
        user.setEmail(email);
        // Not a valid BCrypt hash: these users never log in, they only get a token.
        user.setPasswordHash("not-a-real-hash");
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }

    private School saveSchool(boolean active) {
        School school = new School();
        school.setName("Test school " + UUID.randomUUID());
        school.setAddress("Test street 1");
        school.setActive(active);
        return schoolRepository.saveAndFlush(school);
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private static String json(String email, String role, Long schoolId) {
        return "{\"name\":\"Jana\",\"surname\":\"Novakova\",\"email\":\"" + email + "\",\"role\":\"" + role
                + "\",\"schoolId\":" + schoolId + "}";
    }
}