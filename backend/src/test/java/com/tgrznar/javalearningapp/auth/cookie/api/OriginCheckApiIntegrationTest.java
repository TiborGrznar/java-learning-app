package com.tgrznar.javalearningapp.auth.cookie.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tgrznar.javalearningapp.testsupport.RefreshCookies;
import com.tgrznar.javalearningapp.testsupport.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * The CSRF check of the cookie endpoints with one trusted origin and CORS switched off, which is the
 * same-origin setup (a reverse proxy or the Vite proxy). With CORS off nothing but this check would stop
 * a cross-origin POST from reaching the controller.
 */
@SpringBootTest(properties = "app.auth.cookie.allowed-origins=https://app.test")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
class OriginCheckApiIntegrationTest {

    private static final String TRUSTED = "https://app.test";
    private static final String PASSWORD = "heslo12345";
    private static final String LOGIN = "/api/v1/auth/login";
    private static final String REFRESH = "/api/v1/auth/refresh";
    private static final String LOGOUT = "/api/v1/auth/logout";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void trustedOrigin_reachesTheController() throws Exception {
        mockMvc.perform(post(REFRESH).header(HttpHeaders.ORIGIN, TRUSTED))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
                // CORS is off, so the answer carries no CORS headers: the frontend is same-origin.
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void trustedOrigin_canLogIn() throws Exception {
        String email = registeredEmail();

        mockMvc.perform(post(LOGIN)
                        .header(HttpHeaders.ORIGIN, TRUSTED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE));
    }

    @Test
    void foreignOrigin_isRefusedByLoginRefreshAndLogout() throws Exception {
        for (String path : new String[] {LOGIN, REFRESH, LOGOUT}) {
            mockMvc.perform(post(path).header(HttpHeaders.ORIGIN, "https://evil.test"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"))
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        }
    }

    @Test
    void nullOriginAndAnotherPortOfALocalhost_areRefused() throws Exception {
        for (String origin : new String[] {"null", "http://localhost:5173", "http://app.test"}) {
            mockMvc.perform(post(REFRESH).header(HttpHeaders.ORIGIN, origin))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"));
        }
    }

    @Test
    void foreignLogout_doesNotRevokeTheToken() throws Exception {
        String token = loginToken();

        mockMvc.perform(post(LOGOUT)
                        .header(HttpHeaders.ORIGIN, "https://evil.test")
                        .cookie(RefreshCookies.cookie(token)))
                .andExpect(status().isForbidden());

        // The forged logout did nothing: the token still works.
        mockMvc.perform(post(REFRESH).cookie(RefreshCookies.cookie(token)))
                .andExpect(status().isOk());
    }

    @Test
    void requestWithoutOrigin_passes() throws Exception {
        mockMvc.perform(post(REFRESH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void endpointsThatDoNotUseTheCookie_areNotChecked() throws Exception {
        // Registration sets no cookie and the password change is authenticated by the Authorization header.
        mockMvc.perform(post("/api/v1/auth/register")
                        .header(HttpHeaders.ORIGIN, "https://evil.test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Jano","surname":"Novak","email":"%s","password":"%s","confirmPassword":"%s"}
                                """.formatted(uniqueEmail(), PASSWORD, PASSWORD)))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/users/me/password")
                        .header(HttpHeaders.ORIGIN, "https://evil.test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // ---------- helpers ----------

    private static String uniqueEmail() {
        return "origin-" + UUID.randomUUID() + "@example.com";
    }

    private String registeredEmail() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Jano","surname":"Novak","email":"%s","password":"%s","confirmPassword":"%s"}
                                """.formatted(email, PASSWORD, PASSWORD)))
                .andExpect(status().isCreated());
        return email;
    }

    private String loginToken() throws Exception {
        String email = registeredEmail();
        ResultActions login = mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
        return RefreshCookies.tokenFrom(login);
    }
}
