package net.citotech.cito.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class SecurityConfigTest {

    @Test
    void defaultCorsOriginsAllowLocalhostAndLoopbackDevHosts() {
        SecurityConfig securityConfig = new SecurityConfig();
        ReflectionTestUtils.setField(
                securityConfig,
                "allowedOrigins",
                new String[] {"http://localhost:3000", "http://localhost:2019"});

        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/authenticate");
        CorsConfiguration configuration = source.getCorsConfiguration(request);

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins())
                .contains("http://localhost:3000", "http://127.0.0.1:3000", "http://[::1]:3000");
    }

    @Test
    void corsUsesExplicitHeadersAndExposesOperationalHeaders() {
        SecurityConfig securityConfig = new SecurityConfig();
        ReflectionTestUtils.setField(
                securityConfig, "allowedOrigins", new String[] {"http://localhost:3000"});

        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/v2/payments/collections");
        CorsConfiguration configuration = source.getCorsConfiguration(request);

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedHeaders())
                .contains(
                        "X-Request-ID",
                        "X-CPay-Signature",
                        "X-CPay-Environment",
                        "X-CPay-Idempotency-Key",
                        "X-Idempotency-Key")
                .doesNotContain("*");
        assertThat(configuration.getExposedHeaders())
                .contains("X-Request-ID", "Deprecation", "Sunset", "Link");
    }

    @Test
    void apiAuthorizationMatchersAreExplicitAndDoNotPermitEveryApiRoute() {
        assertThat(SecurityConfig.PUBLIC_ANONYMOUS_API_PATTERNS)
                .containsExactly("/api/public/embedded/onboarding/**")
                .doesNotContain("/api/public/**", "/api/**");
        assertThat(SecurityConfig.PUBLIC_SIGNED_API_PATTERNS).doesNotContain("/api/**");
        assertThat(SecurityConfig.PUBLIC_SESSION_API_PATTERNS).doesNotContain("/api/**");
        assertThat(SecurityConfig.PUBLIC_PAGE_AND_LEGACY_PORTAL_PATTERNS).doesNotContain("/api/**");
        assertThat(SecurityConfig.PUBLIC_SESSION_API_PATTERNS).contains("/api/v2/session/me");
        assertThat(SecurityConfig.ADMIN_API_PATTERNS)
                .contains("/api/v2/product-experience/**", "/api/v2/cross-border/**");
    }

    @Test
    void csrfExemptionsDoNotIncludeSessionAuthenticatedPortalApis() {
        assertThat(SecurityConfig.CSRF_EXEMPT_API_PATTERNS)
                .doesNotContain("/api/**", "/api/v2/merchant/**", "/api/v2/portal/**")
                .contains("/api/v2/native/**", "/api/v2/payments/**");
    }

    /**
     * Audit E10: the local Vite dev server target is only a default - production
     * (application-production.properties) overrides {@code csp.connect-src.extra} to blank so the
     * shipped CSP has no localhost carve-out.
     */
    @Test
    void connectSrcExtraDefaultsToTheLocalDevServerButIsBlankable() {
        SecurityConfig securityConfig = new SecurityConfig();
        ReflectionTestUtils.setField(
                securityConfig,
                "cspConnectSrcExtra",
                "http://localhost:8081 http://127.0.0.1:8081");
        assertThat((String) ReflectionTestUtils.invokeMethod(securityConfig, "connectSrcExtra"))
                .isEqualTo(" http://localhost:8081 http://127.0.0.1:8081");

        ReflectionTestUtils.setField(securityConfig, "cspConnectSrcExtra", "");
        assertThat((String) ReflectionTestUtils.invokeMethod(securityConfig, "connectSrcExtra"))
                .isEmpty();
    }
}
