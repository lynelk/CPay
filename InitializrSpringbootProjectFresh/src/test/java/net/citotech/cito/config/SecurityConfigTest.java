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
                .contains("X-Request-ID", "X-CPay-Signature", "X-Idempotency-Key")
                .doesNotContain("*");
        assertThat(configuration.getExposedHeaders())
                .contains("X-Request-ID", "Deprecation", "Sunset", "Link");
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
