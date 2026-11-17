package com.tgrznar.javalearningapp.auth.cookie;

import com.tgrznar.javalearningapp.common.CorsProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Settings of the refresh token cookie (prefix "app.auth.cookie").
 *
 * @param secure         whether the cookie carries the Secure attribute, so a browser sends it only over HTTPS.
 *                       Default true (the safe choice if the setting is forgotten); switch it off only for local
 *                       development over plain HTTP in a browser that refuses Secure cookies there (Safari)
 * @param allowedOrigins origins of the frontend that may call login, refresh and logout (CSRF defence, see
 *                       OriginCheckInterceptor). Checked like the CORS origins (scheme, host, optional port; no
 *                       wildcard, path, query or user info), so a typo stops the startup. The origins of
 *                       app.cors.allowed-origins are trusted too, so a setup with CORS needs no second list
 */
@ConfigurationProperties(prefix = "app.auth.cookie")
public record RefreshCookieProperties(Boolean secure, List<String> allowedOrigins) {

    public RefreshCookieProperties {
        secure = secure == null || secure;
        allowedOrigins = CorsProperties.normalizeOrigins(allowedOrigins, "app.auth.cookie.allowed-origins");
    }
}
