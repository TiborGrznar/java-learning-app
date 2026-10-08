package com.tgrznar.javalearningapp.auth.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class JwtPropertiesTest {

    private static final String SECRET = "c2VjcmV0LXNlY3JldC1zZWNyZXQtc2VjcmV0LXNlY3JldC0xMjM=";

    private final JwtProperties properties =
            new JwtProperties(SECRET, Duration.ofMinutes(15), Duration.ofDays(7));

    @Test
    void toString_doesNotRevealTheSecret() {
        assertThat(properties.toString()).doesNotContain(SECRET);
    }

    @Test
    void toString_keepsTheTokenLifetimesVisible() {
        assertThat(properties.toString()).contains("PT15M").contains("PT168H");
    }

    @Test
    void secret_isStillAvailableToTheCode() {
        assertThat(properties.secret()).isEqualTo(SECRET);
    }
}