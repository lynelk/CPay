package net.citotech.cito.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Narrow security chain for the unauthenticated Cito access-request intake.
 *
 * <p>The endpoint has no ambient authenticated authority, never provisions an account, and is
 * protected by the shared database-backed request limiter. CSRF is disabled only for this exact
 * route so a public applicant can submit before a session exists. All other routes continue through
 * the primary {@link SecurityConfig} chain and its deny-by-default policy.</p>
 */
@Configuration
public class CitoPublicAccessSecurityConfig {

    @Bean
    @Order(0)
    SecurityFilterChain citoPublicAccessFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/public/access-requests")
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }
}
