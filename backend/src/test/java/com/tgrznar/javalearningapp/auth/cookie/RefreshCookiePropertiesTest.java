package com.tgrznar.javalearningapp.auth.cookie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.List;

class RefreshCookiePropertiesTest {

    @Test
    void secure_isOnWhenNotSet() {
        assertThat(new RefreshCookieProperties(null, null).secure()).isTrue();
    }

    @Test
    void secure_canBeSwitchedOff() {
        assertThat(new RefreshCookieProperties(false, null).secure()).isFalse();
    }

    @Test
    void allowedOrigins_isEmptyWhenNotSet() {
        assertThat(new RefreshCookieProperties(true, null).allowedOrigins()).isEmpty();
    }

    @Test
    void allowedOrigins_areNormalizedAndDeduplicated() {
        RefreshCookieProperties properties = new RefreshCookieProperties(true,
                List.of(" HTTPS://App.Example.com/ ", "https://app.example.com", "http://localhost:5173"));

        assertThat(properties.allowedOrigins())
                .containsExactly("https://app.example.com", "http://localhost:5173");
    }

    @Test
    void allowedOrigins_rejectWildcardWithTheNameOfTheSetting() {
        assertThatThrownBy(() -> new RefreshCookieProperties(true, List.of("*")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app.auth.cookie.allowed-origins");
    }

    @Test
    void allowedOrigins_rejectAPath() {
        assertThatThrownBy(() -> new RefreshCookieProperties(true, List.of("https://app.example.com/app")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
