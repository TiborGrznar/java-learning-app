package com.tgrznar.javalearningapp.user.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe binding of the app.admin.* settings from application.yaml.
 * Used only to create the first administrator at startup (see AdminBootstrapService).
 * Email and password are optional: when they are not set, no administrator is created.
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(
        String email,
        String password,
        String name,
        String surname
) {

    private static final String DEFAULT_NAME = "Admin";
    private static final String DEFAULT_SURNAME = "Administrátor";

    public AdminProperties {
        name = (name == null || name.isBlank()) ? DEFAULT_NAME : name.trim();
        surname = (surname == null || surname.isBlank()) ? DEFAULT_SURNAME : surname.trim();
    }

    /**
     * A record's generated toString prints every component, which would put the password
     * into any log line or error message that mentions this object. Mask it explicitly.
     */
    @Override
    public String toString() {
        return "AdminProperties[email=" + email + ", password=" + (password == null ? "null" : "****")
                + ", name=" + name + ", surname=" + surname + "]";
    }
}