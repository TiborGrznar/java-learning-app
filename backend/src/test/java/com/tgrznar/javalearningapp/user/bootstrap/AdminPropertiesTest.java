package com.tgrznar.javalearningapp.user.bootstrap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminPropertiesTest {

    private static final String PASSWORD = "tajne-heslo-123";

    @Test
    void missingNames_fallBackToDefaults() {
        AdminProperties properties = new AdminProperties("admin@example.com", PASSWORD, null, null);

        assertThat(properties.name()).isEqualTo("Admin");
        assertThat(properties.surname()).isEqualTo("Administrátor");
    }

    @Test
    void blankNames_fallBackToDefaults() {
        AdminProperties properties = new AdminProperties("admin@example.com", PASSWORD, "  ", "");

        assertThat(properties.name()).isEqualTo("Admin");
        assertThat(properties.surname()).isEqualTo("Administrátor");
    }

    @Test
    void configuredNames_areKeptAndTrimmed() {
        AdminProperties properties = new AdminProperties("admin@example.com", PASSWORD, " Jano ", " Novák ");

        assertThat(properties.name()).isEqualTo("Jano");
        assertThat(properties.surname()).isEqualTo("Novák");
    }

    @Test
    void toString_neverContainsThePassword() {
        AdminProperties properties = new AdminProperties("admin@example.com", PASSWORD, "Jano", "Novák");

        assertThat(properties.toString())
                .doesNotContain(PASSWORD)
                .contains("password=****")
                .contains("admin@example.com");
    }

    @Test
    void toString_withoutPasswordShowsNull() {
        AdminProperties properties = new AdminProperties(null, null, null, null);

        assertThat(properties.toString()).contains("password=null");
    }
}