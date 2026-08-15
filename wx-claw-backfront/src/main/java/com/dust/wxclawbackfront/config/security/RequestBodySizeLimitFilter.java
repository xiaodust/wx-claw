package com.dust.wxclawbackfront.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Limits JSON request bodies at the servlet boundary.
 */
public class RequestBodySizeLimitFilter extends OncePerRequestFilter {

    private final long maxBytes;

    public RequestBodySizeLimitFilter(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isJsonRequest(request)) {
            long contentLength = request.getContentLengthLong();
            if (contentLength > maxBytes) {
                response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"Payload Too Large\"}");
                return;
            }
            request = new LimitedBodyHttpServletRequest(request, maxBytes);
        }
        filterChain.doFilter(request, response);
    }

    private boolean isJsonRequest(HttpServletRequest request) {
        String method = request.getMethod();
        if (!("POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method))) {
            return false;
        }
        String contentType = request.getHeader(HttpHeaders.CONTENT_TYPE);
        return contentType != null && contentType.toLowerCase().contains("application/json");
    }

    private static final class LimitedBodyHttpServletRequest extends HttpServletRequestWrapper {
        private final long maxBytes;

        private LimitedBodyHttpServletRequest(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new LimitedServletInputStream(super.getInputStream(), maxBytes);
        }

        @Override
        public BufferedReader getReader() throws IOException {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }

    private static final class LimitedServletInputStream extends ServletInputStream {
        private final InputStream delegate;
        private final long maxBytes;
        private long total;
        private boolean closed;

        private LimitedServletInputStream(InputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value != -1) {
                add(1);
            }
            return value;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int read = delegate.read(b, off, len);
            if (read > 0) {
                add(read);
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            delegate.close();
        }

        @Override
        public boolean isFinished() {
            return closed;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException("Async read is not supported");
        }

        private void add(int count) throws IOException {
            total += count;
            if (total > maxBytes) {
                throw new IOException("JSON request body exceeds " + maxBytes + " bytes");
            }
        }
    }
}
