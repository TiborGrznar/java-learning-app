package com.tgrznar.javalearningapp.security;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The security filter reads the active flag and the role from the database on every request, so an access token
 * that is still valid does not keep a deactivated, deleted or demoted user in. The role claim of the token is not
 * used for authorization. Uses the test-only endpoints of RoleTestController.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
class PerRequestAccessCheckIntegrationTest {

    private static final String ADMIN_URL = "/api/v1/test-roles/admin";
    private static final String ME_URL = "/api/v1/users/me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestUsers testUsers;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    // ---------- the account ----------

    @Test
    void activeUser_isLetIn() throws Exception {
        User admin = testUsers.user(UserRole.ADMIN).save();

        getAs(ADMIN_URL, token(admin))
                .andExpect(status().isOk())
                .andExpect(content().string("admin"));
    }

    @Test
    void deactivatedUserWithAStillValidToken_returns401() throws Exception {
        User admin = testUsers.user(UserRole.ADMIN).save();
        String token = token(admin);
        admin.setActive(false);
        userRepository.saveAndFlush(admin);

        getAs(ADMIN_URL, token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void reactivatedUser_isLetInAgainWithTheSameToken() throws Exception {
        User admin = testUsers.user(UserRole.ADMIN).save();
        String token = token(admin);
        admin.setActive(false);
        userRepository.saveAndFlush(admin);
        getAs(ADMIN_URL, token).andExpect(status().isUnauthorized());

        admin.setActive(true);
        userRepository.saveAndFlush(admin);

        getAs(ADMIN_URL, token).andExpect(status().isOk());
    }

    @Test
    void deletedUserWithAStillValidToken_returns401() throws Exception {
        User student = testUsers.user(UserRole.STUDENT).save();
        String token = token(student);
        userRepository.delete(student);
        userRepository.flush();

        getAs(ME_URL, token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void tokenOfAnIdThatNeverExisted_returns401() throws Exception {
        User ghost = new User();
        ghost.setId(999_999_999L);
        ghost.setRole(UserRole.ADMIN);

        getAs(ADMIN_URL, token(ghost))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void administratorDeactivatesAUser_whoseOldTokenStopsWorkingAtOnce() throws Exception {
        User admin = testUsers.user(UserRole.ADMIN).save();
        User student = testUsers.user(UserRole.STUDENT).save();
        String studentToken = token(student);
        getAs(ME_URL, studentToken).andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/users/" + student.getId() + "/active")
                        .header("Authorization", "Bearer " + token(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());

        getAs(ME_URL, studentToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // ---------- the role ----------

    @Test
    void demotedAdministratorWithAStillValidToken_losesAdminAccessAtOnce() throws Exception {
        User admin = testUsers.user(UserRole.ADMIN).save();
        String token = token(admin);
        admin.setRole(UserRole.STUDENT);
        userRepository.saveAndFlush(admin);

        getAs(ADMIN_URL, token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void promotedUser_getsTheNewRoleAtOnce_becauseTheRoleIsNotTakenFromTheToken() throws Exception {
        User student = testUsers.user(UserRole.STUDENT).save();
        String token = token(student);
        student.setRole(UserRole.ADMIN);
        userRepository.saveAndFlush(student);

        getAs(ADMIN_URL, token).andExpect(status().isOk());
    }

    @Test
    void roleClaimOfTheToken_isIgnored() throws Exception {
        User student = testUsers.user(UserRole.STUDENT).save();
        // A token with a valid signature whose claim says ADMIN for the id of a student.
        User claimsToBeAdmin = new User();
        claimsToBeAdmin.setId(student.getId());
        claimsToBeAdmin.setRole(UserRole.ADMIN);

        getAs(ADMIN_URL, token(claimsToBeAdmin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    // ---------- helpers ----------

    private String token(User user) {
        return jwtService.generateAccessToken(user);
    }

    private ResultActions getAs(String url, String token) throws Exception {
        return mockMvc.perform(get(url).header("Authorization", "Bearer " + token));
    }
}
