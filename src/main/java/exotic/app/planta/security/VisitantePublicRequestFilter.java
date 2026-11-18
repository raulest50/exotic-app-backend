package exotic.app.planta.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Bounded, instance-local protection for the anonymous registration endpoint. */
public class VisitantePublicRequestFilter extends OncePerRequestFilter {
    private static final int MAX_BODY_BYTES = 4096;
    private static final int MAX_REQUESTS_PER_MINUTE = 120;
    private static final long WINDOW_NANOS = TimeUnit.MINUTES.toNanos(1);
    private long windowStart = System.nanoTime();
    private int requests;

    public static boolean isPublicRegistration(HttpServletRequest request) {
        return "POST".equals(request.getMethod())
                && "/api/public/visitantes".equals(request.getServletPath());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isPublicRegistration(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        long retryAfter = retryAfterSeconds();
        if (retryAfter > 0) {
            response.setHeader("Retry-After", Long.toString(retryAfter));
            reject(response, 429, "Espere un minuto antes de intentar nuevamente.");
            return;
        }
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            reject(response, 413, "El formulario supera el tamaño permitido.");
            return;
        }
        // Also bounds chunked requests, which do not declare Content-Length.
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            reject(response, 413, "El formulario supera el tamaño permitido.");
            return;
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override
            public ServletInputStream getInputStream() {
                ByteArrayInputStream input = new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    @Override public int read() { return input.read(); }
                    @Override public int read(byte[] bytes, int offset, int length) { return input.read(bytes, offset, length); }
                    @Override public boolean isFinished() { return input.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) {
                        try {
                            if (!isFinished()) listener.onDataAvailable();
                            if (isFinished()) listener.onAllDataRead();
                        } catch (IOException exception) {
                            listener.onError(exception);
                        }
                    }
                };
            }

            @Override
            public BufferedReader getReader() {
                return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
            }
        }, response);
    }

    private synchronized long retryAfterSeconds() {
        long elapsed = System.nanoTime() - windowStart;
        if (elapsed >= WINDOW_NANOS) {
            windowStart = System.nanoTime();
            requests = 0;
        }
        if (requests >= MAX_REQUESTS_PER_MINUTE) {
            return TimeUnit.NANOSECONDS.toSeconds(WINDOW_NANOS - elapsed) + 1;
        }
        requests++;
        return 0;
    }

    private static void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
