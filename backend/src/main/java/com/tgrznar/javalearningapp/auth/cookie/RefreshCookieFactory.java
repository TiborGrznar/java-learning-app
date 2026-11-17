package com.tgrznar.javalearningapp.auth.cookie;

import com.tgrznar.javalearningapp.auth.security.JwtProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Builds the Set-Cookie value that carries the refresh token to the browser, and the one that removes it.
 * <p>
 * The cookie is HttpOnly (a script on the page cannot read it), SameSite=Strict (the browser does not send it
 * with a request that starts on another site) and limited to Path=/api/v1/auth, so it travels only with the
 * login, refresh and logout calls. It has no Domain attribute, so only the host that set it receives it. The
 * name has no __Host- or __Secure- prefix on purpose: __Host- demands Path=/ and __Secure- demands Secure,
 * which is configurable here. Max-Age equals the refresh token lifetime; the expiry stored on the server decides.
 * <p>
 * The path must match the public address of the API. A reverse proxy that rewrites the /api prefix would
 * stop the browser from sending the cookie.
 * <p>
 * The removing cookie repeats every attribute of the setting one, because a browser deletes a cookie only
 * when name, path and domain match.
 */
@Slf4j
@Component
public class RefreshCookieFactory {

    public static final String NAME = "jla_refresh_token";
    public static final String PATH = "/api/v1/auth";

    private final boolean secure;
    private final Duration maxAge;

    public RefreshCookieFactory(RefreshCookieProperties properties, JwtProperties jwtProperties) {
        this.secure = properties.secure();
        this.maxAge = jwtProperties.refreshTokenTtl();
        if (!secure) {
            log.warn("The refresh token cookie is sent without the Secure attribute"
                    + " (app.auth.cookie.secure=false). Use this only for local development.");
        }
    }

    /** The cookie that hands a new refresh token to the browser. */
    public ResponseCookie issue(String rawToken) {
        return builder(rawToken).maxAge(maxAge).build();
    }

    /** The cookie that makes the browser delete the refresh token cookie. */
    public ResponseCookie clear() {
        return builder("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder builder(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH);
    }
}
