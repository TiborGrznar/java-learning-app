package com.tgrznar.javalearningapp.module.admin.api;

import com.jayway.jsonpath.JsonPath;
import com.tgrznar.javalearningapp.auth.security.JwtService;
import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.module.CourseModuleRepository;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests of the administrator's module management (/api/v1/modules).
 * Test data is created with unique e-mails and rolled back after each test, order numbers
 * of directly saved modules are high so they do not collide with real content.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminModuleApiIntegrationTest {

    private static final String URL = "/api/v1/modules";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseModuleRepository moduleRepository;

    @Autowired
    private JwtService jwtService;

    // ---------- access control ----------

    @Test
    void list_withoutToken_returns401() throws Exception {
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
    void list_asTeacher_returns403() throws Exception {
        as(get(URL), UserRole.TEACHER)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void get_asStudent_returns403() throws Exception {
        CourseModule module = saveModule("Module", 9_001, true);

        as(get(url(module.getId())), UserRole.STUDENT)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void create_asTeacher_returns403AndCreatesNothing() throws Exception {
        long before = moduleRepository.count();

        as(post(URL).contentType(MediaType.APPLICATION_JSON).content(json("Title", "Description", "# Theory", 1)),
                UserRole.TEACHER)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(moduleRepository.count()).isEqualTo(before);
    }

    @Test
    void create_asStudent_returns403() throws Exception {
        as(post(URL).contentType(MediaType.APPLICATION_JSON).content(json("Title", "Description", "# Theory", 1)),
                UserRole.STUDENT)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void update_asTeacher_returns403AndChangesNothing() throws Exception {
        CourseModule module = saveModule("Original", 9_001, true);

        as(put(url(module.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(json("Hacked", "Hacked", "# Hacked", 1)), UserRole.TEACHER)
                .andExpect(status().isForbidden());

        assertThat(moduleRepository.findById(module.getId()).orElseThrow().getTitle()).isEqualTo("Original");
    }

    @Test
    void setActive_asStudent_returns403AndChangesNothing() throws Exception {
        CourseModule module = saveModule("Module", 9_001, true);

        as(patch(url(module.getId()) + "/active").contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"),
                UserRole.STUDENT)
                .andExpect(status().isForbidden());

        assertThat(moduleRepository.findById(module.getId()).orElseThrow().isActive()).isTrue();
    }

    // ---------- create ----------

    @Test
    void create_returns201WithLocationAndFullBody() throws Exception {
        as(post(URL).contentType(MediaType.APPLICATION_JSON).content(json("Uvod do Javy", "Prvy modul", "# Uvod", 1)),
                UserRole.ADMIN)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/modules/")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Uvod do Javy"))
                .andExpect(jsonPath("$.description").value("Prvy modul"))
                .andExpect(jsonPath("$.theoryContent").value("# Uvod"))
                .andExpect(jsonPath("$.orderNumber").value(1))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void create_trimsTitleAndStoresBlankFieldsAsNull() throws Exception {
        as(post(URL).contentType(MediaType.APPLICATION_JSON).content(json("  Cykly  ", "   ", "   ", 3)),
                UserRole.ADMIN)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Cykly"))
                .andExpect(jsonPath("$.description").isEmpty())
                .andExpect(jsonPath("$.theoryContent").isEmpty());
    }

    @Test
    void create_keepsTheoryUnchanged() throws Exception {
        String theory = "  # Nadpis\n\n    kod\n";

        as(post(URL).contentType(MediaType.APPLICATION_JSON).content(json("Teoria", null, theory, 2)),
                UserRole.ADMIN)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.theoryContent").value(theory));
    }

    @Test
    void create_blankTitle_returns400WithFieldError() throws Exception {
        as(post(URL).contentType(MediaType.APPLICATION_JSON).content(json("   ", "Description", "# Theory", 1)),
                UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    @Test
    void create_missingOrderNumber_returns400WithFieldError() throws Exception {
        as(post(URL).contentType(MediaType.APPLICATION_JSON).content(json("Title", "Description", "# Theory", null)),
                UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.orderNumber").exists());
    }

    @Test
    void create_orderNumberZero_returns400WithFieldError() throws Exception {
        as(post(URL).contentType(MediaType.APPLICATION_JSON).content(json("Title", "Description", "# Theory", 0)),
                UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.orderNumber").exists());
    }

    @Test
    void create_titleTooLong_returns400WithFieldError() throws Exception {
        as(post(URL).contentType(MediaType.APPLICATION_JSON).content(json("a".repeat(256), "Description", "# Theory", 1)),
                UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    @Test
    void create_theoryTooLong_returns400WithFieldError() throws Exception {
        as(post(URL).contentType(MediaType.APPLICATION_JSON).content(json("Title", "Description", "a".repeat(100_001), 1)),
                UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.theoryContent").exists());
    }

    // ---------- read ----------

    @Test
    void list_includesInactiveModulesWithoutTheory() throws Exception {
        CourseModule active = saveModule("Active module", 9_001, true);
        CourseModule inactive = saveModule("Inactive module", 9_002, false);

        as(get(URL), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + active.getId() + ")].active").value(true))
                .andExpect(jsonPath("$[?(@.id == " + inactive.getId() + ")].active").value(false))
                .andExpect(jsonPath("$[?(@.id == " + inactive.getId() + ")].theoryContent").doesNotExist());
    }

    @Test
    void list_isOrderedByOrderNumberThenId() throws Exception {
        CourseModule later = saveModule("Later", 9_902, true);
        CourseModule firstOfTwo = saveModule("First of two", 9_901, true);
        CourseModule secondOfTwo = saveModule("Second of two", 9_901, true);

        MvcResult result = as(get(URL), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andReturn();
        List<Number> ids = JsonPath.read(result.getResponse().getContentAsString(), "$[*].id");
        List<Long> idList = ids.stream().map(Number::longValue).toList();

        assertThat(idList.indexOf(firstOfTwo.getId())).isNotNegative();
        assertThat(idList.indexOf(firstOfTwo.getId())).isLessThan(idList.indexOf(secondOfTwo.getId()));
        assertThat(idList.indexOf(secondOfTwo.getId())).isLessThan(idList.indexOf(later.getId()));
    }

    @Test
    void get_returnsFullModule() throws Exception {
        CourseModule module = saveModule("Detail module", 9_001, true);

        as(get(url(module.getId())), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(module.getId()))
                .andExpect(jsonPath("$.title").value("Detail module"))
                .andExpect(jsonPath("$.theoryContent").value("# Theory of Detail module"))
                .andExpect(jsonPath("$.orderNumber").value(9_001))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void get_inactiveModule_isVisibleToAdmin() throws Exception {
        CourseModule module = saveModule("Hidden module", 9_001, false);

        as(get(url(module.getId())), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void get_unknownModule_returns404() throws Exception {
        as(get(url(999_999_999L)), UserRole.ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_FOUND"));
    }

    // ---------- update ----------

    @Test
    void update_replacesAllFields() throws Exception {
        CourseModule module = saveModule("Original", 9_001, true);

        as(put(url(module.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(json(" New title ", "New description", "## New theory", 7)), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New title"))
                .andExpect(jsonPath("$.description").value("New description"))
                .andExpect(jsonPath("$.theoryContent").value("## New theory"))
                .andExpect(jsonPath("$.orderNumber").value(7));

        assertThat(moduleRepository.findById(module.getId()).orElseThrow().getTitle()).isEqualTo("New title");
    }

    @Test
    void update_keepsTheActiveFlag() throws Exception {
        CourseModule module = saveModule("Hidden module", 9_001, false);

        as(put(url(module.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(json("Still hidden", "Description", "# Theory", 1)), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void update_unknownModule_returns404() throws Exception {
        as(put(url(999_999_999L)).contentType(MediaType.APPLICATION_JSON)
                .content(json("Title", "Description", "# Theory", 1)), UserRole.ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_FOUND"));
    }

    @Test
    void update_blankTitle_returns400WithFieldError() throws Exception {
        CourseModule module = saveModule("Original", 9_001, true);

        as(put(url(module.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(json("", "Description", "# Theory", 1)), UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    // ---------- activate / deactivate ----------

    @Test
    void setActive_deactivatesAndReactivates() throws Exception {
        CourseModule module = saveModule("Module", 9_001, true);

        as(patch(url(module.getId()) + "/active").contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"),
                UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        assertThat(moduleRepository.findById(module.getId()).orElseThrow().isActive()).isFalse();

        as(patch(url(module.getId()) + "/active").contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"),
                UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        assertThat(moduleRepository.findById(module.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void setActive_missingFlag_returns400WithFieldError() throws Exception {
        CourseModule module = saveModule("Module", 9_001, true);

        as(patch(url(module.getId()) + "/active").contentType(MediaType.APPLICATION_JSON).content("{}"),
                UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.active").exists());
    }

    @Test
    void setActive_unknownModule_returns404() throws Exception {
        as(patch(url(999_999_999L) + "/active").contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"),
                UserRole.ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_FOUND"));
    }

    // ---------- helpers ----------

    private ResultActions as(MockHttpServletRequestBuilder request, UserRole role) throws Exception {
        String token = jwtService.generateAccessToken(saveUser(role));
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private User saveUser(UserRole role) {
        User user = new User();
        user.setName("Jano");
        user.setSurname("Novak");
        user.setEmail(("user-" + UUID.randomUUID() + "@example.com").toLowerCase(Locale.ROOT));
        // Not a valid BCrypt hash: these users never log in, they only get a token.
        user.setPasswordHash("not-a-real-hash");
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }

    private CourseModule saveModule(String title, int orderNumber, boolean active) {
        CourseModule module = new CourseModule();
        module.setTitle(title);
        module.setDescription("Description of " + title);
        module.setTheoryContent("# Theory of " + title);
        module.setOrderNumber(orderNumber);
        module.setActive(active);
        return moduleRepository.saveAndFlush(module);
    }

    private static String url(Long id) {
        return URL + "/" + id;
    }

    private static String json(String title, String description, String theory, Integer orderNumber) {
        return "{\"title\":" + quote(title) + ",\"description\":" + quote(description)
                + ",\"theoryContent\":" + quote(theory) + ",\"orderNumber\":" + orderNumber + "}";
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}