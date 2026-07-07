package net.citotech.cito.config;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Value("${cors.allowed-origins:http://localhost:3000,http://127.0.0.1:3000,http://[::1]:3000,http://localhost:2019,http://127.0.0.1:2019,http://[::1]:2019}")
    private String[] allowedOrigins;

    @Value("${actuator.username}")
    private String actuatorUsername;

    @Value("${actuator.password}")
    private String actuatorPassword;

    @Value("${admin.api.username:${actuator.username}}")
    private String adminUsername;

    @Value("${admin.api.password:${actuator.password}}")
    private String adminPassword;

    @Bean
    @SuppressWarnings("java/spring-disabled-csrf-protection")
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Legacy SPA/session endpoints do not yet exchange CSRF tokens; keep
        // existing behavior until the frontend can send Spring's token header.
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v2/admin/**").hasRole("ADMIN")
                .requestMatchers("/actuator/**").hasRole("ACTUATOR")
                .anyRequest().permitAll()
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.ALWAYS))
            .httpBasic(httpBasic -> {});
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        validateCredentials(actuatorUsername, actuatorPassword, "ACTUATOR_USERNAME and ACTUATOR_PASSWORD must be set");
        validateCredentials(adminUsername, adminPassword, "ADMIN_API_USERNAME and ADMIN_API_PASSWORD must be set");

        if (actuatorUsername.equalsIgnoreCase(adminUsername)) {
            UserDetails combinedUser = User.withUsername(actuatorUsername)
                .password(encoder.encode(actuatorPassword))
                .roles("ACTUATOR", "ADMIN")
                .build();
            return new InMemoryUserDetailsManager(combinedUser);
        }

        UserDetails actuatorUser = User.withUsername(actuatorUsername)
            .password(encoder.encode(actuatorPassword))
            .roles("ACTUATOR")
            .build();
        UserDetails adminUser = User.withUsername(adminUsername)
            .password(encoder.encode(adminPassword))
            .roles("ADMIN")
            .build();
        return new InMemoryUserDetailsManager(actuatorUser, adminUser);
    }

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration trustedConfig = new CorsConfiguration();
        trustedConfig.setAllowedOrigins(expandLoopbackAliases(allowedOrigins));
        trustedConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        trustedConfig.setAllowedHeaders(List.of("*"));
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
        return "localhost".equalsIgnoreCase(host)
            || "127.0.0.1".equals(host)
            || "::1".equals(host);
    }

    private void validateCredentials(String username, String password, String message) {
        if (isBlank(username) || isBlank(password)) throw new IllegalStateException(message);
    }

    private boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
}

