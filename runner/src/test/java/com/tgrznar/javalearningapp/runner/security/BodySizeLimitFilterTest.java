package com.tgrznar.javalearningapp.runner.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tgrznar.javalearningapp.runner.sandbox.config.SandboxCapacityProperties;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class BodySizeLimitFilterTest {

    /** Smallest archive limit: 6 * 1024 + 16 KiB of JSON overhead. */
    private final BodySizeLimitFilter filter = new BodySizeLimitFilter(
            new SandboxCapacityProperties(1, 0, 300, 60, 1024, 1024, 1024, 15, 10));
    private final long limit = filter.maxBodyBytes();

    /** A request that does not declare its length, like a chunked one. */
    private static final class ChunkedRequest extends MockHttpServletRequest {
        ChunkedRequest(String method, String uri, byte[] body) {
            super(method, uri);
            setContent(body);
        }

        @Override
        public long getContentLengthLong() {
            return -1;
        }

        @Override
        public int getContentLength() {
            return -1;
        }
    }

    private static MockHttpServletRequest post(byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/run");
        request.setContent(body);
        return request;
    }

    @Test
    void limit_isSixTimesTheArchiveLimitPlusOverhead() {
        assertThat(limit).isEqualTo(6L * 1024 + 16 * 1024);
    }

    @Test
    void declaredLengthOverTheLimit_isRefusedWithoutCallingTheChain() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();

        filter.doFilter(post(new byte[(int) limit + 1]), response, (req, res) -> called.set(true));

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).isEqualTo("{\"error\":\"PAYLOAD_TOO_LARGE\"}");
        assertThat(called).isFalse();
    }

    @Test
    void bodyExactlyAtTheLimit_passesAndCanBeRead() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean read = new AtomicBoolean();

        filter.doFilter(post(new byte[(int) limit]), response, (req, res) -> {
            assertThat(req.getInputStream().readAllBytes()).hasSize((int) limit);
            read.set(true);
        });

        assertThat(read).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void chunkedBodyOverTheLimit_failsWhileItIsRead() {
        MockHttpServletRequest request = new ChunkedRequest("POST", "/run", new byte[(int) limit + 1]);

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> req.getInputStream().readAllBytes()))
                .isInstanceOf(RequestBodyTooLargeException.class);
    }

    @Test
    void chunkedBodyOverTheLimit_failsAlsoWhenReadThroughTheReader() {
        MockHttpServletRequest request = new ChunkedRequest("POST", "/run", new byte[(int) limit + 1]);

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> req.getReader().transferTo(java.io.Writer.nullWriter())))
                .isInstanceOf(IOException.class);
    }

    @Test
    void otherPathsAndMethods_areNotLimited() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();
        MockHttpServletRequest health = new MockHttpServletRequest("GET", "/health");
        health.setContent(new byte[(int) limit + 1]);

        filter.doFilter(health, response, (req, res) -> called.set(true));

        assertThat(called).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
