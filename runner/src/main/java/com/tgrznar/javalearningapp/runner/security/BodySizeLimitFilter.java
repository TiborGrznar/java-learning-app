package com.tgrznar.javalearningapp.runner.security;

import com.tgrznar.javalearningapp.runner.sandbox.config.SandboxCapacityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Caps the size of the body of POST /run before it is read into memory.
 *
 * <p>The limit follows from the sandbox limits: the content that can reach the container is at most
 * maxArchiveBytes, and JSON may spell every byte as a six-character escape sequence, so the body
 * may legitimately be about six times larger, plus the JSON structure. A declared Content-Length
 * over the limit is refused at once; a body without one (chunked) is counted while it is read.
 * Runs after the token filter, so only the backend gets this far.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class BodySizeLimitFilter extends OncePerRequestFilter {

    private static final String PROTECTED_PATH = "/run";
    private static final long JSON_OVERHEAD_BYTES = 16 * 1024;
    private static final long ESCAPE_FACTOR = 6;

    private final long maxBodyBytes;

    public BodySizeLimitFilter(SandboxCapacityProperties capacity) {
        this.maxBodyBytes = ESCAPE_FACTOR * capacity.maxArchiveBytes() + JSON_OVERHEAD_BYTES;
    }

    long maxBodyBytes() {
        return maxBodyBytes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !PROTECTED_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBodyBytes) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"PAYLOAD_TOO_LARGE\"}");
            return;
        }
        chain.doFilter(new LimitedRequest(request, maxBodyBytes), response);
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {

        private final long limit;
        private ServletInputStream stream;

        LimitedRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                stream = new CountingStream(super.getInputStream(), limit);
            }
            return stream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding() == null ? "UTF-8" : getCharacterEncoding();
            return new BufferedReader(new InputStreamReader(getInputStream(), encoding));
        }
    }

    private static final class CountingStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long limit;
        private long count;

        CountingStream(ServletInputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value >= 0) {
                add(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = delegate.read(buffer, offset, length);
            if (read > 0) {
                add(read);
            }
            return read;
        }

        private void add(int bytes) throws IOException {
            count += bytes;
            if (count > limit) {
                throw new RequestBodyTooLargeException(limit);
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }
    }
}