package com.kazikonnect.backend.core.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Puts a correlation id on every request so a single user action can be traced across
 * log lines, including the asynchronous payment and payout paths where several log
 * statements from different threads would otherwise be impossible to correlate.
 *
 * <p>Honours an inbound {@code X-Request-Id}/{@code X-Correlation-Id} so ids issued by a
 * gateway or the frontend survive into backend logs, and echoes the id back on the
 * response so a client can quote it in a bug report.
 *
 * <p>Registered ahead of the security filter chain so authentication failures are logged
 * with an id too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_PATH = "path";

    /** Bounded to keep a hostile client from writing unbounded junk into every log line. */
    private static final int MAX_INBOUND_ID_LENGTH = 64;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        MDC.put(MDC_REQUEST_ID, requestId);
        MDC.put(MDC_PATH, request.getMethod() + " " + request.getRequestURI());
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Threads are pooled and reused, so a leaked MDC entry would mislabel the
            // next unrelated request handled by this thread.
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_PATH);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String inbound = firstNonBlank(request.getHeader(REQUEST_ID_HEADER), request.getHeader(CORRELATION_ID_HEADER));
        if (inbound == null) {
            return UUID.randomUUID().toString();
        }
        String trimmed = inbound.trim();
        if (trimmed.length() > MAX_INBOUND_ID_LENGTH) {
            trimmed = trimmed.substring(0, MAX_INBOUND_ID_LENGTH);
        }
        // Strip anything that could forge a log line or break structured parsing.
        return trimmed.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }
}
