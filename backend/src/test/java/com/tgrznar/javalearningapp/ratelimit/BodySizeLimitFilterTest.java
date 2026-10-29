package com.tgrznar.javalearningapp.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

class BodySizeLimitFilterTest {

    // default limit 100 bytes, large limit 1000 bytes for /api/v1/modules
    private final BodySizeLimitFilter filter = new BodySizeLimitFilter(
            TestRateLimitProperties.create(TestRateLimitProperties.OFF, TestRateLimitProperties.OFF,
                    TestRateLimitProperties.OFF), new ObjectMapper());

    private static MockHttpServletRequest request(String path, int bodyBytes) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setContent(new byte[bodyBytes]);
        return request;
    }

    @Test
    void bodyWithinTheLimit_passes() throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("/api/v1/auth/login", 100), response, (rq, rs) -> called.set(true));

        assertThat(called).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void declaredLengthOverTheLimit_isRefusedWith413BeforeTheChain() throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("/api/v1/auth/login", 101), response, (rq, rs) -> called.set(true));

        assertThat(called).isFalse();
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("\"code\":\"PAYLOAD_TOO_LARGE\"");
    }

    @Test
    void largePaths_getTheLargerLimit() throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("/api/v1/modules/5", 900), response, (rq, rs) -> called.set(true));

        assertThat(called).isTrue();
        MockHttpServletResponse tooBig = new MockHttpServletResponse();
        filter.doFilter(request("/api/v1/modules/5", 1001), tooBig, (rq, rs) -> { });
        assertThat(tooBig.getStatus()).isEqualTo(413);
    }

    @Test
    void aPathThatOnlyStartsWithTheLargePrefix_getsTheDefaultLimit() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("/api/v1/modules-evil", 500), response, (rq, rs) -> { });

        assertThat(response.getStatus()).isEqualTo(413);
    }

    @Test
    void pathsOutsideTheApi_areNotChecked() throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("/health", 5000), response, (rq, rs) -> called.set(true));

        assertThat(called).isTrue();
    }

    /** A request that hides its length, like a chunked upload. */
    private static HttpServletRequest chunked(MockHttpServletRequest request) {
        return new HttpServletRequestWrapper(request) {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
    }

    @Test
    void streamedBodyOverTheLimit_failsWhileReading() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        FilterChain chain = (rq, rs) -> {
            try {
                rq.getInputStream().readAllBytes();
            } catch (Exception e) {
                failure.set(e);
            }
        };

        filter.doFilter(chunked(request("/api/v1/auth/login", 200)), new MockHttpServletResponse(), chain);

        assertThat(failure.get()).isInstanceOf(RequestBodyTooLargeException.class);
    }

    @Test
    void streamedBodyWithinTheLimit_isReadInFull() throws Exception {
        AtomicReference<byte[]> read = new AtomicReference<>();
        FilterChain chain = (rq, rs) -> read.set(rq.getInputStream().readAllBytes());

        filter.doFilter(chunked(request("/api/v1/auth/login", 100)), new MockHttpServletResponse(), chain);

        assertThat(read.get()).hasSize(100);
        assertThatThrownBy(() -> { throw new RequestBodyTooLargeException(); })
                .isInstanceOf(java.io.IOException.class);
    }
}