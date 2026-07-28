package net.citotech.cito.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String REQUEST_ID_KEY = "request_id";
    private static final String METHOD_KEY = "http_method";
    private static final String PATH_KEY = "http_path";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        MDC.put(REQUEST_ID_KEY, requestId);
        MDC.put(METHOD_KEY, request.getMethod());
        MDC.put(PATH_KEY, request.getRequestURI());
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(PATH_KEY);
            MDC.remove(METHOD_KEY);
            MDC.remove(REQUEST_ID_KEY);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String value = request.getHeader(REQUEST_ID_HEADER);
        if (value == null || value.trim().isEmpty()) {
            return UUID.randomUUID().toString();
        }
        return value.trim();
    }
}
