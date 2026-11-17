package com.tgrznar.javalearningapp.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Cross-origin settings (prefix "app.cors"). CORS is off by default: with no origins configured the
 * API sends no CORS headers, which is what a same-origin setup needs (the Vite dev proxy or a reverse
 * proxy that serves the frontend and /api from one address).
 * <p>
 * Every origin is checked at startup and the application does not start with a wrong one. An origin is
 * scheme, host and an optional port (for example https://app.example.com or http://localhost:5173).
 * A wildcard, a path, a query and user info are refused, so a typo can never open the API to every site.
 * Origins are stored in a normalized form (lower case, no trailing slash, no duplicates).
 * The same rules are used for the origins of the refresh token cookie checks (RefreshCookieProperties).
 *
 * @param allowedOrigins exact origins that may call the API from a browser; empty = CORS off
 * @param maxAge         how long a browser may cache the answer to a preflight request (default 1 hour)
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins, Duration maxAge) {

    private static final Duration DEFAULT_MAX_AGE = Duration.ofHours(1);

    public CorsProperties {
        allowedOrigins = normalizeOrigins(allowedOrigins, "app.cors.allowed-origins");
        maxAge = maxAge == null ? DEFAULT_MAX_AGE : maxAge;
        if (maxAge.isNegative()) {
            throw new IllegalArgumentException("app.cors.max-age must not be negative");
        }
    }

    /** True when at least one origin is allowed. */
    public boolean enabled() {
        return !allowedOrigins.isEmpty();
    }

    /** Normalizes a list of origins (blank entries are skipped, duplicates removed); {@code property} names the setting in error messages. */
    public static List<String> normalizeOrigins(List<String> origins, String property) {
        if (origins == null) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String raw : origins) {
            if (raw != null && !raw.isBlank()) {
                result.add(normalizeOrigin(raw.trim(), property));
            }
        }
        return List.copyOf(result);
    }

    /**
     * Returns the origin in its normalized form.
     *
     * @throws IllegalArgumentException if it is not a plain origin (scheme http or https, host, optional port)
     */
    public static String normalizeOrigin(String origin, String property) {
        URI uri;
        try {
            uri = new URI(origin);
        } catch (URISyntaxException e) {
            throw invalid(origin, property);
        }
        String scheme = uri.getScheme();
        String path = uri.getRawPath();
        boolean httpScheme = scheme != null
                && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
        boolean noPath = path == null || path.isEmpty() || path.equals("/");
        if (!httpScheme || uri.getHost() == null || !noPath
                || uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getRawUserInfo() != null) {
            throw invalid(origin, property);
        }
        String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
        return scheme.toLowerCase(Locale.ROOT) + "://" + uri.getHost().toLowerCase(Locale.ROOT) + port;
    }

    private static IllegalArgumentException invalid(String origin, String property) {
        return new IllegalArgumentException("Invalid " + property + " entry '" + origin
                + "': expected an origin such as https://app.example.com or http://localhost:5173"
                + " (scheme, host and an optional port; no wildcard, path, query or user info)");
    }
}