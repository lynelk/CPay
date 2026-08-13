package net.citotech.cito.config;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
// Audit E3: enables @PreAuthorize as defense-in-depth on top of the path-based rules in
// filterChain() below. This is additive hardening only - every method-level check added under
// this flag mirrors an existing path-matcher rule (e.g. hasRole("ADMIN") for /api/v2/admin/**),
// so it can never grant access the path matcher wouldn't already grant; it only protects against
// the path rule drifting or being bypassed by an internal forward/include.
@EnableMethodSecurity
public class SecurityConfig {
    @Value(
            "${cors.allowed-origins:http://localhost:3000,http://127.0.0.1:3000,http://[::1]:3000,http://localhost:2019,http://127.0.0.1:2019,http://[::1]:2019}")
    private String[] allowedOrigins;

    @Value("${actuator.username}")
    private String actuatorUsername;

    @Value("${actuator.password}")
    private String actuatorPassword;

    @Value("${admin.api.username}")
    private String adminUsername;

    @Value("${admin.api.password}")
    private String adminPassword;

    // Audit E10: the Vite-built SPA (Clientside/build/index.html) loads its bundle via an external
    // <script type="module"> and stylesheet <link> with no inline <script> anywhere, so script-src
    // does not need 'unsafe-inline' - only style-src does, because the React component library
    // sets `style={{...}}` (a DOM inline-style attribute) extensively. This extra connect-src
    // allowance is the local Vite dev server target only; production overrides it to blank in
    // application-production.properties so the shipped CSP has no localhost carve-out.
    @Value("${csp.connect-src.extra:http://localhost:8081 http://127.0.0.1:8081}")
    private String cspConnectSrcExtra;

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http, LegacySessionAuthorizationFilter legacySessionAuthorizationFilter)
            throws Exception {
        CsrfTokenRequestAttributeHandler csrfRequestHandler =
                new CsrfTokenRequestAttributeHandler();

        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(
                        csrf ->
                                csrf.csrfTokenRepository(
                                                CookieCsrfTokenRepository.withHttpOnlyFalse())
                                        .csrfTokenRequestHandler(csrfRequestHandler)
                                        .ignoringRequestMatchers(
                                                "/api/v1/**",
                                                "/api/do*",
                                                "/api/test*",
                                                "/api/v2/admin/**",
                                                "/api/v2/balances",
                                                "/api/v2/channels",
                                                "/api/v2/health",
                                                "/api/v2/merchant/**",
                                                "/api/v2/merchant-self-service/signup",
                                                "/api/v2/native/**",
                                                "/api/v2/payments/**",
                                                "/api/v2/production-maturity/**",
                                                "/api/v2/vending/**",
                                                "/actuator/**",
                                                "/status/**"))
                .headers(
                        headers ->
                                headers.contentTypeOptions(contentType -> {})
                                        .frameOptions(frame -> frame.sameOrigin())
                                        .referrerPolicy(
                                                referrer ->
                                                        referrer.policy(
                                                                ReferrerPolicyHeaderWriter
                                                                        .ReferrerPolicy
                                                                        .SAME_ORIGIN))
                                        .httpStrictTransportSecurity(
                                                hsts ->
                                                        hsts.includeSubDomains(true)
                                                                .maxAgeInSeconds(31536000))
                                        .contentSecurityPolicy(
                                                csp ->
                                                        csp.policyDirectives(
                                                                "default-src 'self'; "
                                                                        + "script-src 'self'; "
                                                                        + "style-src 'self' 'unsafe-inline'; "
                                                                        + "img-src 'self' data: https:; "
                                                                        + "font-src 'self' data:; "
                                                                        + "connect-src 'self'"
                                                                        + connectSrcExtra()
                                                                        + "; "
                                                                        + "frame-ancestors 'self'; "
                                                                        + "base-uri 'self'; "
                                                                        + "form-action 'self'")))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                                        .permitAll()
                                        .requestMatchers(
                                                "/api/v2/admin/**",
                                                "/api/v2/production-maturity/**")
                                        .hasRole("ADMIN")
                                        .requestMatchers("/actuator/**")
                                        .hasRole("ACTUATOR")
                                        .requestMatchers(
                                                "/",
                                                "/dashboard",
                                                "/dashboardMerchant",
                                                "/portal",
                                                "/vending/rent/**",
                                                "/api/**",
                                                "/auth/**",
                                                "/admins/**",
                                                "/audittrail/**",
                                                "/merchants/**",
                                                "/settings/**",
                                                "/status/**",
                                                "/transactions/**")
                                        .permitAll()
                                        .anyRequest()
                                        .denyAll())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.ALWAYS))
                .addFilterBefore(legacySessionAuthorizationFilter, AuthorizationFilter.class)
                .httpBasic(httpBasic -> {});
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        validateCredentials(
                actuatorUsername,
                actuatorPassword,
                "ACTUATOR_USERNAME and ACTUATOR_PASSWORD must be set");
        validateCredentials(
                adminUsername,
                adminPassword,
                "ADMIN_API_USERNAME and ADMIN_API_PASSWORD must be set");

        if (actuatorUsername.equalsIgnoreCase(adminUsername)) {
            UserDetails combinedUser =
                    User.withUsername(actuatorUsername)
                            .password(encoder.encode(actuatorPassword))
                            .roles("ACTUATOR", "ADMIN")
                            .build();
            return new InMemoryUserDetailsManager(combinedUser);
        }

        UserDetails actuatorUser =
                User.withUsername(actuatorUsername)
                        .password(encoder.encode(actuatorPassword))
                        .roles("ACTUATOR")
                        .build();
        UserDetails adminUser =
                User.withUsername(adminUsername)
                        .password(encoder.encode(adminPassword))
                        .roles("ADMIN")
                        .build();
        return new InMemoryUserDetailsManager(actuatorUser, adminUser);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration trustedConfig = new CorsConfiguration();
        trustedConfig.setAllowedOrigins(expandLoopbackAliases(allowedOrigins));
        trustedConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        trustedConfig.setAllowedHeaders(
                List.of(
                        "Authorization",
                        "Content-Type",
                        "X-CSRF-TOKEN",
                        "X-CPay-Merchant",
                        "X-CPay-Signature",
                        "X-CPay-Timestamp",
                        "X-CPay-Nonce",
                        "X-Idempotency-Key",
                        "Idempotency-Key",
                        "X-Request-ID"));
        trustedConfig.setExposedHeaders(List.of("X-Request-ID", "Deprecation", "Sunset", "Link"));
        trustedConfig.setAllowCredentials(true);
        trustedConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", trustedConfig);
        source.registerCorsConfiguration("/**", trustedConfig);
        return source;
    }

    private List<String> expandLoopbackAliases(String[] origins) {
        Set<String> expandedOrigins = new LinkedHashSet<>();
        for (String origin : origins) {
            if (isBlank(origin)) {
                continue;
            }
            String trimmedOrigin = origin.trim();
            expandedOrigins.add(trimmedOrigin);
            addLoopbackAlias(expandedOrigins, trimmedOrigin, "localhost");
            addLoopbackAlias(expandedOrigins, trimmedOrigin, "127.0.0.1");
            addLoopbackAlias(expandedOrigins, trimmedOrigin, "[::1]");
        }
        return List.copyOf(expandedOrigins);
    }

    private void addLoopbackAlias(Set<String> origins, String origin, String aliasHost) {
        try {
            URI uri = URI.create(origin);
            String host = uri.getHost();
            if (!isLoopbackHost(host)) {
                return;
            }
            String scheme = uri.getScheme();
            int port = uri.getPort();
            if (isBlank(scheme) || port < 0) {
                return;
            }
            origins.add(scheme + "://" + aliasHost + ":" + port);
        } catch (IllegalArgumentException ignored) {
            // Leave any non-URI origin unchanged; Spring will validate it later.
        }
    }

    private boolean isLoopbackHost(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
    }

    private void validateCredentials(String username, String password, String message) {
        if (isBlank(username) || isBlank(password)) throw new IllegalStateException(message);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String connectSrcExtra() {
        return isBlank(cspConnectSrcExtra) ? "" : " " + cspConnectSrcExtra.trim();
    }
}
