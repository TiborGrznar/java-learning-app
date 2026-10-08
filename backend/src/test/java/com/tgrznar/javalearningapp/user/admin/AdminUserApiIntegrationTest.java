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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests of the administrator's user management (UC-04): create, list, detail, edit,
 * role change and (de)activation. E-mails and school names are unique per test (UUID).
 * Each test is rolled back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminUserApiIntegrationTest {

    private static final String URL = "/api/v1/users";
    private static final String PASSWORD = "Password123!";

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

    // ======================================================================
    // POST /api/v1/users (create)
    // ======================================================================

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

    // ======================================================================
    // Access control of the other endpoints
    // ======================================================================

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
    void get_asTeacher_returns403() throws Exception {
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT);

        as(get(url(target.getId())), UserRole.TEACHER)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void update_asTeacher_returns403AndChangesNothing() throws Exception {
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT);

        as(put(url(target.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(updateJson("Hacked", "Hacked", uniqueEmail(), null)), UserRole.TEACHER)
                .andExpect(status().isForbidden());

        assertThat(userRepository.findById(target.getId()).orElseThrow().getName()).isEqualTo("Jano");
    }

    @Test
    void changeRole_asTeacher_returns403AndChangesNothing() throws Exception {
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT);

        as(patch(url(target.getId()) + "/role").contentType(MediaType.APPLICATION_JSON)
                .content(roleJson("ADMIN", null)), UserRole.TEACHER)
                .andExpect(status().isForbidden());

        assertThat(userRepository.findById(target.getId()).orElseThrow().getRole()).isEqualTo(UserRole.STUDENT);
    }

    @Test
    void setActive_asTeacher_returns403AndChangesNothing() throws Exception {
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT);

        as(patch(url(target.getId()) + "/active").contentType(MediaType.APPLICATION_JSON)
                .content(activeJson(false)), UserRole.TEACHER)
                .andExpect(status().isForbidden());

        assertThat(userRepository.findById(target.getId()).orElseThrow().isActive()).isTrue();
    }

    // ======================================================================
    // GET /api/v1/users (list)
    // ======================================================================

    @Test
    void list_returnsPageShapeWithoutPasswordHash() throws Exception {
        as(get(URL).param("size", "1"), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber())
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
    }

    @Test
    void list_filtersBySchool() throws Exception {
        School school = saveSchoolWithThreeUsers();

        as(get(URL).param("schoolId", school.getId().toString()), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].schoolName").value(school.getName()));
    }

    @Test
    void list_filtersByRole() throws Exception {
        School school = saveSchoolWithThreeUsers();

        as(get(URL).param("schoolId", school.getId().toString()).param("role", "TEACHER"), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].surname").value("Beta"));
    }

    @Test
    void list_filtersByActiveFlag() throws Exception {
        School school = saveSchoolWithThreeUsers();

        as(get(URL).param("schoolId", school.getId().toString()).param("active", "false"), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].surname").value("Gama"));
    }

    @Test
    void list_isSortedBySurname() throws Exception {
        School school = saveSchoolWithThreeUsers();

        as(get(URL).param("schoolId", school.getId().toString()), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].surname").value("Alfa"))
                .andExpect(jsonPath("$.content[1].surname").value("Beta"))
                .andExpect(jsonPath("$.content[2].surname").value("Gama"));
    }

    @Test
    void list_paginates() throws Exception {
        School school = saveSchoolWithThreeUsers();

        as(get(URL).param("schoolId", school.getId().toString()).param("size", "2"), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2));

        as(get(URL).param("schoolId", school.getId().toString()).param("size", "2").param("page", "1"),
                UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].surname").value("Gama"));
    }

    @Test
    void list_tooLargeSize_isCappedAtMaximum() throws Exception {
        as(get(URL).param("size", "1000"), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void list_negativePage_isTreatedAsFirstPage() throws Exception {
        as(get(URL).param("page", "-1"), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void list_unknownRoleFilter_returns400() throws Exception {
        // Known gap: the response is Spring's default error body, not our JSON (see open-decisions.md).
        as(get(URL).param("role", "SUPERHERO"), UserRole.ADMIN)
                .andExpect(status().isBadRequest());
    }

    // ======================================================================
    // GET /api/v1/users/{id}
    // ======================================================================

    @Test
    void get_returnsUser() throws Exception {
        School school = saveSchool(true);
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.TEACHER, school, "Alfa", true);

        as(get(url(target.getId())), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(target.getId()))
                .andExpect(jsonPath("$.email").value(target.getEmail()))
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andExpect(jsonPath("$.schoolId").value(school.getId()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void get_unknownUser_returns404() throws Exception {
        as(get(url(999999999L)), UserRole.ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    // ======================================================================
    // PUT /api/v1/users/{id}
    // ======================================================================

    @Test
    void update_changesFieldsAndNormalizesEmail() throws Exception {
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT);
        String newEmail = "New-" + UUID.randomUUID() + "@Example.COM";

        as(put(url(target.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(updateJson(" Peter ", " Horvath ", newEmail, null)), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Peter"))
                .andExpect(jsonPath("$.surname").value("Horvath"))
                .andExpect(jsonPath("$.email").value(newEmail.toLowerCase(Locale.ROOT)))
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    void update_teacherCanMoveToAnotherActiveSchool() throws Exception {
        School oldSchool = saveSchool(true);
        School newSchool = saveSchool(true);
        User teacher = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.TEACHER, oldSchool, "Alfa", true);

        as(put(url(teacher.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(updateJson("Jana", "Novakova", teacher.getEmail(), newSchool.getId())), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schoolId").value(newSchool.getId()))
                .andExpect(jsonPath("$.schoolName").value(newSchool.getName()));
    }

    @Test
    void update_keepingOwnEmail_isNotADuplicate() throws Exception {
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT);

        as(put(url(target.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(updateJson("Peter", "Horvath", target.getEmail(), null)), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(target.getEmail()));
    }

    @Test
    void update_emailOfAnotherUser_returns409() throws Exception {
        User other = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT);
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT);

        as(put(url(target.getId())).contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("Peter", "Horvath", other.getEmail().toUpperCase(Locale.ROOT), null)),
                UserRole.ADMIN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void update_teacherWithoutSchool_returns400WithFieldError() throws Exception {
        School school = saveSchool(true);
        User teacher = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.TEACHER, school, "Alfa", true);

        as(put(url(teacher.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(updateJson("Jana", "Novakova", teacher.getEmail(), null)), UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.schoolId").exists());
    }

    @Test
    void update_toInactiveSchool_returns409() throws Exception {
        School inactive = saveSchool(false);
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT);

        as(put(url(target.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(updateJson("Peter", "Horvath", target.getEmail(), inactive.getId())), UserRole.ADMIN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHOOL_INACTIVE"));
    }

    @Test
    void update_unknownUser_returns404() throws Exception {
        as(put(url(999999999L)).contentType(MediaType.APPLICATION_JSON)
                .content(updateJson("Peter", "Horvath", uniqueEmail(), null)), UserRole.ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void update_invalidEmail_returns400WithFieldError() throws Exception {
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT);

        as(put(url(target.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(updateJson("Peter", "Horvath", "not-an-email", null)), UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    // ======================================================================
    // PATCH /api/v1/users/{id}/role
    // ======================================================================

    @Test
    void changeRole_studentToTeacher_returns200() throws Exception {
        School school = saveSchool(true);
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT);

        as(patch(url(target.getId()) + "/role").contentType(MediaType.APPLICATION_JSON)
                .content(roleJson("TEACHER", school.getId())), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andExpect(jsonPath("$.schoolId").value(school.getId()));
    }

    @Test
    void changeRole_toTeacherWithoutSchool_returns400WithFieldError() throws Exception {
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT);

        as(patch(url(target.getId()) + "/role").contentType(MediaType.APPLICATION_JSON)
                .content(roleJson("TEACHER", null)), UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.schoolId").exists());
    }

    @Test
    void changeRole_ownAccount_returns409() throws Exception {
        User admin = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.ADMIN);

        asUser(patch(url(admin.getId()) + "/role").contentType(MediaType.APPLICATION_JSON)
                .content(roleJson("STUDENT", null)), admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_MODIFICATION_NOT_ALLOWED"));

        assertThat(userRepository.findById(admin.getId()).orElseThrow().getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void changeRole_lastActiveAdmin_returns409() throws Exception {
        User actor = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.ADMIN);
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.ADMIN);
        makeOnlyActiveAdmin(target);

        asUser(patch(url(target.getId()) + "/role").contentType(MediaType.APPLICATION_JSON)
                .content(roleJson("STUDENT", null)), actor)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN"));

        assertThat(userRepository.findById(target.getId()).orElseThrow().getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void changeRole_missingRole_returns400WithFieldError() throws Exception {
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT);

        as(patch(url(target.getId()) + "/role").contentType(MediaType.APPLICATION_JSON).content("{}"),
                UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.role").exists());
    }

    @Test
    void changeRole_unknownUser_returns404() throws Exception {
        as(patch(url(999999999L) + "/role").contentType(MediaType.APPLICATION_JSON)
                .content(roleJson("STUDENT", null)), UserRole.ADMIN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    // ======================================================================
    // PATCH /api/v1/users/{id}/active
    // ======================================================================

    @Test
    void setActive_deactivatesUser() throws Exception {
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT);

        as(patch(url(target.getId()) + "/active").contentType(MediaType.APPLICATION_JSON).content(activeJson(false)),
                UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        assertThat(userRepository.findById(target.getId()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void setActive_reactivatesUser() throws Exception {
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT, null, "Alfa", false);

        as(patch(url(target.getId()) + "/active").contentType(MediaType.APPLICATION_JSON).content(activeJson(true)),
                UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void setActive_ownAccount_returns409() throws Exception {
        User admin = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.ADMIN);

        asUser(patch(url(admin.getId()) + "/active").contentType(MediaType.APPLICATION_JSON).content(activeJson(false)),
                admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_MODIFICATION_NOT_ALLOWED"));

        assertThat(userRepository.findById(admin.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void setActive_lastActiveAdmin_returns409() throws Exception {
        User actor = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.ADMIN);
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.ADMIN);
        makeOnlyActiveAdmin(target);

        asUser(patch(url(target.getId()) + "/active").contentType(MediaType.APPLICATION_JSON).content(activeJson(false)),
                actor)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN"));

        assertThat(userRepository.findById(target.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void setActive_missingFlag_returns400WithFieldError() throws Exception {
        User target = saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT);

        as(patch(url(target.getId()) + "/active").contentType(MediaType.APPLICATION_JSON).content("{}"),
                UserRole.ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.active").exists());
    }

    @Test
    void deactivatedUser_cannotLogIn() throws Exception {
        User target = saveUserWithPassword(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT);

        as(patch(url(target.getId()) + "/active").contentType(MediaType.APPLICATION_JSON).content(activeJson(false)),
                UserRole.ADMIN)
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(target.getEmail())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void deactivatedUser_cannotRefreshTheSession() throws Exception {
        User target = saveUserWithPassword(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT);

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(target.getEmail())))
                .andExpect(status().isOk())
                .andReturn();
        String refreshToken = JsonPath.read(login.getResponse().getContentAsString(), "$.refreshToken");

        as(patch(url(target.getId()) + "/active").contentType(MediaType.APPLICATION_JSON).content(activeJson(false)),
                UserRole.ADMIN)
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    // ======================================================================
    // helpers
    // ======================================================================

    private ResultActions as(MockHttpServletRequestBuilder request, UserRole role) throws Exception {
        return asUser(request, saveUser(uniqueEmail().toLowerCase(Locale.ROOT), role));
    }

    private ResultActions asUser(MockHttpServletRequestBuilder request, User actor) throws Exception {
        String token = jwtService.generateAccessToken(actor);
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private User saveUser(String email, UserRole role) {
        return saveUser(email, role, null, "Novak", true);
    }

    private User saveUser(String email, UserRole role, School school, String surname, boolean active) {
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

    private User saveUserWithPassword(String email, UserRole role) {
        User user = saveUser(email, role);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return userRepository.saveAndFlush(user);
    }

    private School saveSchool(boolean active) {
        School school = new School();
        school.setName("Test school " + UUID.randomUUID());
        school.setAddress("Test street 1");
        school.setActive(active);
        return schoolRepository.saveAndFlush(school);
    }

    /** A school with a teacher (Beta), an active student (Alfa) and an inactive student (Gama). */
    private School saveSchoolWithThreeUsers() {
        School school = saveSchool(true);
        saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.TEACHER, school, "Beta", true);
        saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT, school, "Alfa", true);
        saveUser(uniqueEmail().toLowerCase(Locale.ROOT), UserRole.STUDENT, school, "Gama", false);
        return school;
    }

    /**
     * Makes the target the only active administrator in the database (rolled back after the test).
     * The acting administrator is deactivated too, which works because access tokens are stateless
     * and is_active is not checked per request (known gap in open-decisions.md). When that check
     * is added, this helper has to be rewritten.
     */
    private void makeOnlyActiveAdmin(User target) {
        userRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.ADMIN && u.isActive() && !u.getId().equals(target.getId()))
                .forEach(u -> u.setActive(false));
        userRepository.flush();
    }

    private static String url(Long id) {
        return URL + "/" + id;
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private static String json(String email, String role, Long schoolId) {
        return "{\"name\":\"Jana\",\"surname\":\"Novakova\",\"email\":\"" + email + "\",\"role\":\"" + role
                + "\",\"schoolId\":" + schoolId + "}";
    }

    private static String updateJson(String name, String surname, String email, Long schoolId) {
        return "{\"name\":\"" + name + "\",\"surname\":\"" + surname + "\",\"email\":\"" + email
                + "\",\"schoolId\":" + schoolId + "}";
    }

    private static String roleJson(String role, Long schoolId) {
        return "{\"role\":\"" + role + "\",\"schoolId\":" + schoolId + "}";
    }

    private static String activeJson(boolean active) {
        return "{\"active\":" + active + "}";
    }

    private static String loginJson(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}";
    }
}