package com.tgrznar.javalearningapp.auth.cookie;

import static org.assertj.core.api.Assertions.assertThat;

import com.tgrznar.javalearningapp.auth.security.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

class RefreshCookieFactoryTest {

    private static final JwtProperties JWT = new JwtProperties(
            "x".repeat(43), Duration.ofMinutes(15), Duration.ofDays(7));

    private static RefreshCookieFactory factory(boolean secure) {
        return new RefreshCookieFactory(new RefreshCookieProperties(secure, null), JWT);
    }

    @Test
    void issue_setsAllSecurityAttributes() {
        ResponseCookie cookie = factory(true).issue("raw-token");

        assertThat(cookie.getName()).isEqualTo("jla_refresh_token");
        assertThat(cookie.getValue()).isEqualTo("raw-token");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cookie.getDomain()).isNull();
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void issue_headerValueCarriesTheAttributes() {
        String header = factory(true).issue("raw-token").toString();

        assertThat(header)
                .startsWith("jla_refresh_token=raw-token;")
                .contains("Path=/api/v1/auth")
                .contains("Max-Age=604800")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .doesNotContain("Domain");
    }

    @Test
    void issue_withSecureOff_omitsTheSecureAttribute() {
        ResponseCookie cookie = factory(false).issue("raw-token");

        assertThat(cookie.isSecure()).isFalse();
        assertThat(cookie.toString()).doesNotContain("Secure");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
    }

    @Test
    void clear_repeatsTheAttributesSoTheBrowserDeletesTheCookie() {
        ResponseCookie cookie = factory(true).clear();

        assertThat(cookie.getName()).isEqualTo("jla_refresh_token");
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.toString()).contains("Max-Age=0");
    }
}
