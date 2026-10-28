package com.tgrznar.javalearningapp.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

class RateLimitFilterTest {

    private final RateLimitProperties properties = TestRateLimitProperties.create(3, 2, 5);
    private final RateLimitFilter filter = filterTrusting(List.of());

    private RateLimitFilter filterTrusting(List<String> proxies) {
        return new RateLimitFilter(new RateLimiters(properties), new ClientIpResolver(proxies), new ObjectMapper());
    }

    private static MockHttpServletRequest request(String method, String path, String remote) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(remote);
        return request;
    }

    /** Runs one request through the filter; the response is 200 when the filter let it pass. */
    private MockHttpServletResponse call(RateLimitFilter f, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        f.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void login_isLimitedPerIpWithRetryAfterAndJsonBody() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(call(filter, request("POST", "/api/v1/auth/login", "1.1.1.1")).getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse blocked = call(filter, request("POST", "/api/v1/auth/login", "1.1.1.1"));

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("900");
        assertThat(blocked.getContentType()).startsWith("application/json");
        assertThat(blocked.getContentAsString()).contains("\"code\":\"RATE_LIMITED\"");
    }

    @Test
    void differentIps_haveSeparateCounters() throws Exception {
        for (int i = 0; i < 3; i++) {
            call(filter, request("POST", "/api/v1/auth/login", "1.1.1.1"));
        }

        assertThat(call(filter, request("POST", "/api/v1/auth/login", "2.2.2.2")).getStatus()).isEqualTo(200);
    }

    @Test
    void register_hasItsOwnCounter() throws Exception {
        for (int i = 0; i < 3; i++) {
            call(filter, request("POST", "/api/v1/auth/login", "1.1.1.1"));
        }

        assertThat(call(filter, request("POST", "/api/v1/auth/register", "1.1.1.1")).getStatus()).isEqualTo(200);
        call(filter, request("POST", "/api/v1/auth/register", "1.1.1.1"));
        assertThat(call(filter, request("POST", "/api/v1/auth/register", "1.1.1.1")).getStatus()).isEqualTo(429);
    }

    @Test
    void otherApiRequests_fallUnderTheGeneralLimit() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(call(filter, request("GET", "/api/v1/users/me", "1.1.1.1")).getStatus()).isEqualTo(200);
        }

        assertThat(call(filter, request("GET", "/api/v1/anything-new", "1.1.1.1")).getStatus()).isEqualTo(429);
    }

    @Test
    void pathsOutsideTheApi_areNotLimited() throws Exception {
        for (int i = 0; i < 20; i++) {
            assertThat(call(filter, request("GET", "/error", "1.1.1.1")).getStatus()).isEqualTo(200);
        }
    }

    @Test
    void behindATrustedProxy_theClientAddressIsTheKey() throws Exception {
        RateLimitFilter proxied = filterTrusting(List.of("10.0.0.5"));
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = request("POST", "/api/v1/auth/login", "10.0.0.5");
            request.addHeader("X-Forwarded-For", "198.51.100.9");
            call(proxied, request);
        }

        MockHttpServletRequest sameClient = request("POST", "/api/v1/auth/login", "10.0.0.5");
        sameClient.addHeader("X-Forwarded-For", "198.51.100.9");
        MockHttpServletRequest otherClient = request("POST", "/api/v1/auth/login", "10.0.0.5");
        otherClient.addHeader("X-Forwarded-For", "198.51.100.10");

        assertThat(call(proxied, sameClient).getStatus()).isEqualTo(429);
        assertThat(call(proxied, otherClient).getStatus()).isEqualTo(200);
    }
}