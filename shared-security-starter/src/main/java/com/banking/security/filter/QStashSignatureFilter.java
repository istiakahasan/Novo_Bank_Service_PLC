package com.banking.security.filter;

import com.banking.security.handler.JsonErrorWriter;
import com.banking.security.util.QStashSignatureVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Authenticates /webhooks/** requests by their QStash signature BEFORE any controller (or JWT logic) runs.
 * The raw body is read once, verified, and replayed to the controller through a small caching wrapper.
 * The header is Upstash-Signature (what QStash sends); QStash-Signature is accepted as an alias.
 */
@Component
public class QStashSignatureFilter extends OncePerRequestFilter {

    static final int MAX_BODY_BYTES = 1_048_576;
    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final QStashSignatureVerifier verifier;

    public QStashSignatureFilter(QStashSignatureVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !MATCHER.match("/webhooks/**", path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            JsonErrorWriter.write(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Request body too large");
            return;
        }
        String signature = request.getHeader("Upstash-Signature");
        if (signature == null) {
            signature = request.getHeader("QStash-Signature");
        }
        if (!verifier.verify(body, signature)) {
            JsonErrorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid or missing signature");
            return;
        }
        chain.doFilter(new CachedBodyRequest(request, body), response);
    }

    /** Replays the already-read body so the controller can still use @RequestBody. */
    static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream source = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return source.read();
                }

                @Override
                public boolean isFinished() {
                    return source.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Async reads are not supported");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
