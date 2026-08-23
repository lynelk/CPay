package net.citotech.cito.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Narrow security chain for unauthenticated Cito entry points.
 *
 * <p>The access-request endpoint cannot provision an account and is protected by the shared
 * database-backed request limiter. The embedded-onboarding route is read-only and protected by a
 * high-entropy, expiring token whose hash is stored server side. CSRF is disabled only for these
 * explicitly matched public routes. All other routes continue through the primary {@link
 * SecurityConfig} chain and its deny-by-default policy.</p>
 */
@Configuration
public class CitoPublicAccessSecurityConfig {

    @Bean
    @Order(0)
    SecurityFilterChain citoPublicAccessFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(
                        "/api/public/access-requests", "/api/public/embedded/onboarding/**")
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }
}