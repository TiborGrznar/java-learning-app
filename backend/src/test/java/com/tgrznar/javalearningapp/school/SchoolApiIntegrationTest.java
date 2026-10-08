package com.tgrznar.javalearningapp.school;

import com.tgrznar.javalearningapp.auth.security.JwtService;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests of /api/v1/schools: role checks, validation, duplicates and the happy paths.
 * School names are unique per test (UUID) so they never clash with data in the dev database.
 * Each test runs in a transaction that is rolled back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SchoolApiIntegrationTest {

    private static final String URL = "/api/v1/schools";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private JwtService jwtService;

    // ---------- 401 / 403 ----------

    @Test
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void list_asStudent_returns403() throws Exception {
        as(get(URL), UserRole.STUDENT)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void create_asTeacher_returns403AndSavesNothing() throws Exception {
        String name = uniqueName();

        as(post(URL).contentType(MediaType.APPLICATION_JSON).content(json(name, "Street 1")), UserRole.TEACHER)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        org.assertj.core.api.Assertions.assertThat(schoolRepository.existsByNameIgnoreCase(name)).isFalse();
    }

    @Test
    void get_asTeacher_returns403() throws Exception {
        School school = saveSchool(uniqueName(), true);

        as(get(URL + "/" + school.getId()), UserRole.TEACHER)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void update_asStudent_returns403() throws Exception {
        School school = saveSchool(uniqueName(), true);

        as(put(URL + "/" + school.getId()).contentType(MediaType.APPLICATION_JSON).content(json(uniqueName(), "X")),
                UserRole.STUDENT)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void setActive_asTeacher_returns403() throws Exception {
        School school = saveSchool(uniqueName(), true);

        as(patch(URL + "/" + school.getId() + "/active").contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"), UserRole.TEACHER)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    // ---------- create ----------

    @Test
    void create_asAdmin_returns201WithLocationAndBody() throws Exception {
        String name = uniqueName();

        as(post(URL).contentType(MediaType.APPLICATION_JSON).content(json("  " + name + "  ", "  Hlavna 1  ")),
                UserRole.ADMIN)
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.address").value("Hlavna 1"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void create_duplicateNameDifferentCase_returns409() throws Exception {
        String name = uniqueName();
        saveSchool(name, true);

        as(post(URL).contentType(MediaType.APPLICATION_JSON).content(json(name.toUpperCase(), "Street 1")),
                UserRole.ADMIN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHOOL_NAME_ALREADY_EXISTS"));
    }

    @Test
    void create_blankName_returns400WithFieldError() throws Exception {
        as(post(URL).contentType(MediaType.APPLICATION_JSON).content(json("   ", "Street 1")), UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void create_missingAddress_returns400WithFieldError() throws Exception {
        as(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + uniqueName() + "\"}"),
                UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.address").exists());
    }

    @Test
    void create_nameLongerThan255_returns400() throws Exception {
        as(post(URL).contentType(MediaType.APPLICATION_JSON).content(json("a".repeat(256), "Street 1")),
                UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    // ---------- list / get ----------

    @Test
    void list_asAdmin_containsSavedSchool() throws Exception {
        String name = uniqueName();
        saveSchool(name, true);

        as(get(URL), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == '" + name + "')]").exists());
    }

    @Test
    void list_activeTrue_hidesInactiveSchool() throws Exception {
        String activeName = uniqueName();
        String inactiveName = uniqueName();
        saveSchool(activeName, true);
        saveSchool(inactiveName, false);

        as(get(URL + "?active=true"), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == '" + activeName + "')]").exists())
                .andExpect(jsonPath("$[?(@.name == '" + inactiveName + "')]").doesNotExist());
    }

    @Test
    void get_existingSchool_returns200() throws Exception {
        School school = saveSchool(uniqueName(), true);

        as(get(URL + "/" + school.getId()), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(school.getName()));
    }

    @Test
    void get_unknownId_returns404() throws Exception {
        as(get(URL + "/999999999"), UserRole.ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCHOOL_NOT_FOUND"));
    }

    // ---------- update ----------

    @Test
    void update_changesNameAndAddress() throws Exception {
        School school = saveSchool(uniqueName(), true);
        String newName = uniqueName();

        as(put(URL + "/" + school.getId()).contentType(MediaType.APPLICATION_JSON).content(json(newName, "New 5")),
                UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(newName))
                .andExpect(jsonPath("$.address").value("New 5"));
    }

    @Test
    void update_keepingOwnNameWithNewAddress_returns200() throws Exception {
        School school = saveSchool(uniqueName(), true);

        as(put(URL + "/" + school.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(json(school.getName(), "Changed 7")), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value("Changed 7"));
    }

    @Test
    void update_nameOfAnotherSchool_returns409() throws Exception {
        School first = saveSchool(uniqueName(), true);
        School second = saveSchool(uniqueName(), true);

        as(put(URL + "/" + second.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(json(first.getName(), "Street 1")), UserRole.ADMIN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHOOL_NAME_ALREADY_EXISTS"));
    }

    @Test
    void update_unknownId_returns404() throws Exception {
        as(put(URL + "/999999999").contentType(MediaType.APPLICATION_JSON).content(json(uniqueName(), "X")),
                UserRole.ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCHOOL_NOT_FOUND"));
    }

    // ---------- active flag ----------

    @Test
    void setActive_false_deactivatesSchool() throws Exception {
        School school = saveSchool(uniqueName(), true);

        as(patch(URL + "/" + school.getId() + "/active").contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void setActive_true_reactivatesSchool() throws Exception {
        School school = saveSchool(uniqueName(), false);

        as(patch(URL + "/" + school.getId() + "/active").contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":true}"), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void setActive_missingValue_returns400() throws Exception {
        School school = saveSchool(uniqueName(), true);

        as(patch(URL + "/" + school.getId() + "/active").contentType(MediaType.APPLICATION_JSON).content("{}"),
                UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.active").exists());
    }

    @Test
    void setActive_unknownId_returns404() throws Exception {
        as(patch(URL + "/999999999/active").contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"),
                UserRole.ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCHOOL_NOT_FOUND"));
    }

    // ---------- helpers ----------

    private ResultActions as(MockHttpServletRequestBuilder request, UserRole role) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + jwtService.generateAccessToken(saveUser(role))));
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

    private School saveSchool(String name, boolean active) {
        School school = new School();
        school.setName(name);
        school.setAddress("Test street 1");
        school.setActive(active);
        return schoolRepository.saveAndFlush(school);
    }

    private static String uniqueName() {
        return "Test school " + UUID.randomUUID();
    }

    private static String json(String name, String address) {
        return "{\"name\":\"" + name + "\",\"address\":\"" + address + "\"}";
    }
}