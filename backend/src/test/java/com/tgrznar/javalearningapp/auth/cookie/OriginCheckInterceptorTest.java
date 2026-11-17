package com.tgrznar.javalearningapp.auth.cookie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tgrznar.javalearningapp.auth.exception.OriginNotAllowedException;
import com.tgrznar.javalearningapp.common.CorsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

class OriginCheckInterceptorTest {

    private static OriginCheckInterceptor interceptor(List<String> corsOrigins, List<String> cookieOrigins) {
        return new OriginCheckInterceptor(
                new CorsProperties(corsOrigins, null),
                new RefreshCookieProperties(true, cookieOrigins));
    }

    private static boolean call(OriginCheckInterceptor interceptor, String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
        if (origin != null) {
            request.addHeader(HttpHeaders.ORIGIN, origin);
        }
        return interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
    }

    @Test
    void missingOrigin_passes() {
        assertThat(call(interceptor(List.of(), List.of()), null)).isTrue();
    }

    @Test
    void originFromTheCookieList_passes() {
        assertThat(call(interceptor(List.of(), List.of("https://app.example.com")),
                "https://app.example.com")).isTrue();
    }

    @Test
    void originFromTheCorsList_passes() {
        assertThat(call(interceptor(List.of("http://localhost:5173"), List.of()),
                "http://localhost:5173")).isTrue();
    }

    @Test
    void originIsComparedInNormalizedForm() {
        assertThat(call(interceptor(List.of(), List.of("https://app.example.com")),
                "HTTPS://APP.example.com")).isTrue();
    }

    @Test
    void foreignOrigin_isRefused() {
        OriginCheckInterceptor interceptor = interceptor(List.of(), List.of("https://app.example.com"));

        assertThatThrownBy(() -> call(interceptor, "https://evil.example.org"))
                .isInstanceOf(OriginNotAllowedException.class);
    }

    @Test
    void samePortOnAnotherScheme_isRefused() {
        OriginCheckInterceptor interceptor = interceptor(List.of(), List.of("https://app.example.com"));

        assertThatThrownBy(() -> call(interceptor, "http://app.example.com"))
                .isInstanceOf(OriginNotAllowedException.class);
    }

    @Test
    void siblingSubdomain_isRefused() {
        OriginCheckInterceptor interceptor = interceptor(List.of(), List.of("https://app.example.com"));

        assertThatThrownBy(() -> call(interceptor, "https://other.example.com"))
                .isInstanceOf(OriginNotAllowedException.class);
    }

    @Test
    void nullOrigin_isRefused() {
        OriginCheckInterceptor interceptor = interceptor(List.of(), List.of("https://app.example.com"));

        assertThatThrownBy(() -> call(interceptor, "null"))
                .isInstanceOf(OriginNotAllowedException.class);
    }

    @Test
    void garbageOrigin_isRefused() {
        OriginCheckInterceptor interceptor = interceptor(List.of(), List.of("https://app.example.com"));

        assertThatThrownBy(() -> call(interceptor, "not an origin"))
                .isInstanceOf(OriginNotAllowedException.class);
    }

    @Test
    void noTrustedOrigin_refusesAnyOrigin() {
        OriginCheckInterceptor interceptor = interceptor(List.of(), List.of());

        assertThatThrownBy(() -> call(interceptor, "https://app.example.com"))
                .isInstanceOf(OriginNotAllowedException.class);
    }
}
