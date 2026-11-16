package com.tgrznar.javalearningapp.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CorsPropertiesTest {

    @Test
    void noOrigins_meansCorsIsOff() {
        assertThat(new CorsProperties(null, null).enabled()).isFalse();
        assertThat(new CorsProperties(List.of(), null).allowedOrigins()).isEmpty();
    }

    @Test
    void blankEntries_areIgnored() {
        // An empty environment variable (CORS_ALLOWED_ORIGINS=) must mean "off", not an error.
        CorsProperties properties = new CorsProperties(List.of("", "   "), null);

        assertThat(properties.allowedOrigins()).isEmpty();
        assertThat(properties.enabled()).isFalse();
    }

    @Test
    void origins_areNormalized() {
        CorsProperties properties = new CorsProperties(
                List.of(" HTTPS://App.Example.com/ ", "http://localhost:5173", "https://app.example.com"), null);

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.allowedOrigins()).containsExactly("https://app.example.com", "http://localhost:5173");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "*",
            "https://*.example.com",
            "localhost:5173",
            "ftp://app.example.com",
            "https://app.example.com/path",
            "https://app.example.com?x=1",
            "https://app.example.com#top",
            "https://user@app.example.com",
            "https://",
            "not an origin"})
    void wrongOrigin_isRefusedAtStartup(String origin) {
        assertThatThrownBy(() -> new CorsProperties(List.of(origin), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app.cors.allowed-origins");
    }

    @Test
    void oneWrongOrigin_refusesTheWholeList() {
        assertThatThrownBy(() -> new CorsProperties(List.of("https://app.example.com", "*"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void maxAge_defaultsToOneHour() {
        assertThat(new CorsProperties(List.of(), null).maxAge()).isEqualTo(Duration.ofHours(1));
        assertThat(new CorsProperties(List.of(), Duration.ofMinutes(5)).maxAge()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void negativeMaxAge_isRefused() {
        assertThatThrownBy(() -> new CorsProperties(List.of(), Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-age");
    }
}
