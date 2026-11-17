package com.tgrznar.javalearningapp.testsupport;

import com.tgrznar.javalearningapp.auth.cookie.RefreshCookieFactory;
import jakarta.servlet.http.Cookie;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;

/** Test helpers for the refresh token cookie: sending it with a request and reading it from a response. */
public final class RefreshCookies {

    private RefreshCookies() {
    }

    /** The cookie a browser would send with a refresh or logout request. */
    public static Cookie cookie(String refreshToken) {
        return new Cookie(RefreshCookieFactory.NAME, refreshToken);
    }

    /** The raw refresh token set by the response; fails the test when the response sets no such cookie. */
    public static String tokenFrom(MockHttpServletResponse response) {
        String prefix = RefreshCookieFactory.NAME + "=";
        for (String header : response.getHeaders(HttpHeaders.SET_COOKIE)) {
            if (header.startsWith(prefix)) {
                String value = header.substring(prefix.length());
                int end = value.indexOf(';');
                return end < 0 ? value : value.substring(0, end);
            }
        }
        throw new AssertionError("The response sets no " + RefreshCookieFactory.NAME + " cookie");
    }

    public static String tokenFrom(ResultActions result) {
        return tokenFrom(result.andReturn().getResponse());
    }
}
