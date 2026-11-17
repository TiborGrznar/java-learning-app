package com.tgrznar.javalearningapp.auth.cookie.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tgrznar.javalearningapp.auth.cookie.RefreshCookieFactory;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * The refresh token cookie on the real endpoints, with the default settings (Secure on, no trusted origin,
 * CORS off). The Origin check with a configured origin is in OriginCheckApiIntegrationTest.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
class RefreshCookieApiIntegrationTest {

    private static final String PASSWORD = "heslo12345";
    private static final String LOGIN = "/api/v1/auth/login";
    private static final String REFRESH = "/api/v1/auth/refresh";
    private static final String LOGOUT = "/api/v1/auth/logout";

    @Autowired
    private MockMvc mockMvc;

    // ---------- login ----------

    @Test
    void login_setsTheRefreshCookieWithAllSecurityAttributes() throws Exception {
        String email = registeredEmail();

        MockHttpServletResponse response = login(email)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn().getResponse();

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .startsWith(RefreshCookieFactory.NAME + "=")
                .contains("Path=/api/v1/auth", "Max-Age=604800", "HttpOnly", "Secure", "SameSite=Strict")
                .doesNotContain("Domain");
        String token = RefreshCookies.tokenFrom(response);
        assertThat(token).isNotBlank();
        assertThat(response.getContentAsString()).doesNotContain(token);
    }

    @Test
    void failedLogin_setsNoCookie() throws Exception {
        login(registeredEmail(), "zle-heslo-123")
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    // ---------- refresh ----------

    @Test
    void refresh_replacesTheCookieAndTheAnswerMustNotBeCached() throws Exception {
        String first = RefreshCookies.tokenFrom(login(registeredEmail()).andExpect(status().isOk()));

        ResultActions refreshed = refresh(first)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());

        String second = RefreshCookies.tokenFrom(refreshed);
        assertThat(second).isNotBlank().isNotEqualTo(first);
        assertThat(refreshed.andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .contains("Path=/api/v1/auth", "HttpOnly", "Secure", "SameSite=Strict");
    }

    @Test
    void refresh_withoutCookie_returns401AndSetsNoCookie() throws Exception {
        mockMvc.perform(post(REFRESH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void refresh_withBlankCookie_returns401() throws Exception {
        refresh("   ")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void refresh_ignoresATokenSentInTheBody() throws Exception {
        String token = RefreshCookies.tokenFrom(login(registeredEmail()).andExpect(status().isOk()));

        mockMvc.perform(post(REFRESH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + token + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void refresh_withAnUnknownCookie_returns401AndDoesNotDeleteTheCookie() throws Exception {
        // A failed refresh must not touch the cookie: a second tab may have just received a new one,
        // and deleting it here would log that tab out.
        refresh("neexistujuci-token")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    // ---------- logout ----------

    @Test
    void logout_revokesTheTokenAndDeletesTheCookie() throws Exception {
        String token = RefreshCookies.tokenFrom(login(registeredEmail()).andExpect(status().isOk()));

        MockHttpServletResponse response = mockMvc.perform(post(LOGOUT).cookie(RefreshCookies.cookie(token)))
                .andExpect(status().isNoContent())
                .andReturn().getResponse();

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .startsWith(RefreshCookieFactory.NAME + "=")
                .contains("Max-Age=0", "Path=/api/v1/auth", "HttpOnly", "Secure", "SameSite=Strict");
        assertThat(RefreshCookies.tokenFrom(response)).isEmpty();
        refresh(token).andExpect(status().isUnauthorized());
    }

    @Test
    void logout_withoutCookie_succeedsAndStillDeletesTheCookie() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post(LOGOUT))
                .andExpect(status().isNoContent())
                .andReturn().getResponse();

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    }

    // ---------- Origin check without a trusted origin (fail closed) ----------

    @Test
    void anyOrigin_isRefusedByLoginRefreshAndLogoutWhenNoOriginIsTrusted() throws Exception {
        for (String path : new String[] {LOGIN, REFRESH, LOGOUT}) {
            mockMvc.perform(post(path).header(HttpHeaders.ORIGIN, "https://app.test"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"))
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        }
    }

    // ---------- helpers ----------

    private String registeredEmail() throws Exception {
        String email = "cookie-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Jano","surname":"Novak","email":"%s","password":"%s","confirmPassword":"%s"}
                                """.formatted(email, PASSWORD, PASSWORD)))
                .andExpect(status().isCreated());
        return email;
    }

    private ResultActions login(String email) throws Exception {
        return login(email, PASSWORD);
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password)));
    }

    private ResultActions refresh(String token) throws Exception {
        return mockMvc.perform(post(REFRESH).cookie(RefreshCookies.cookie(token)));
    }
}
