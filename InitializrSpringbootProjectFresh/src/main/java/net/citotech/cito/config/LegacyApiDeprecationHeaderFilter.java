package net.citotech.cito.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class LegacyApiDeprecationHeaderFilter extends OncePerRequestFilter {
    @Value("${cpay.api.legacy.sunset-date:2027-01-31}")
    private String sunsetDate;

    @Value("${cpay.api.legacy.docs-url:/docs/api-v2-signing.md}")
    private String migrationDocsUrl;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isLegacyApi(request.getRequestURI())) {
            response.setHeader("Deprecation", "true");
            response.setHeader("Sunset", safeSunsetDate());
            response.addHeader("Link", "<" + migrationDocsUrl + ">; rel=\"deprecation\"; type=\"text/markdown\"");
            response.addHeader("Link", "<" + migrationDocsUrl + ">; rel=\"successor-version\"; type=\"text/markdown\"");
        }
        filterChain.doFilter(request, response);
    }

    private boolean isLegacyApi(String path) {
        return path != null && (path.startsWith("/api/v1/") || path.startsWith("/api/do"));
    }

    private String safeSunsetDate() {
        try {
            return LocalDate.parse(sunsetDate).toString();
        } catch (RuntimeException ex) {
            return "2027-01-31";
        }
    }
}
