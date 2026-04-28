package net.citotech.cito.config;

import java.util.Arrays;
import java.util.List;
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

/**
 * Spring Security configuration.
 *
 * <ul>
 *   <li>CORS: {@code /api/**} allows all origins (merchant clients, RSA-authenticated).
 *       All other paths allow only the origins listed in {@code cors.allowed-origins}.</li>
 *   <li>CSRF: Disabled globally for now (the external {@code /api/**} endpoints use
 *       RSA-signature authentication; the session-based admin portal would need
 *       a frontend update to send the XSRF-TOKEN cookie as a header).</li>
 *   <li>Actuator endpoints require HTTP Basic authentication with the
 *       {@code ACTUATOR} role.  Set {@code actuator.username} and
 *       {@code actuator.password} in your environment variables.</li>
 *   <li>All other requests are permitted; application-level session checks remain
 *       in the individual controllers.</li>
 * </ul>
 *
 * <p>TODO: Enable CSRF protection once the React frontend is updated to read the
 * {@code XSRF-TOKEN} cookie and send the token in the {@code X-XSRF-TOKEN}
 * request header.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:2019}")
    private String[] allowedOrigins;

    @Value("${actuator.username:actuator}")
    private String actuatorUsername;

    @Value("${actuator.password:changeme_in_production}")
    private String actuatorPassword;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors()
            .and()
            // TODO: Enable CSRF after updating the React frontend to send the
            // XSRF-TOKEN cookie value in the X-XSRF-TOKEN header.
            .csrf().disable()
            .authorizeHttpRequests(auth -> auth
                .antMatchers("/actuator/**").hasRole("ACTUATOR")
                .anyRequest().permitAll()
            )
            .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.ALWAYS)
            .and()
            .httpBasic(); // Used only for /actuator/** endpoints
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        UserDetails actuatorUser = User.withUsername(actuatorUsername)
                .password(encoder.encode(actuatorPassword))
                .roles("ACTUATOR")
                .build();
        return new InMemoryUserDetailsManager(actuatorUser);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS rules:
     * <ul>
     *   <li>{@code /api/**}: all origins, no credentials (RSA-signed merchant API)</li>
     *   <li>{@code /**}: configured origins only, with credentials (session-based portals)</li>
     * </ul>
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // Public merchant API – any origin, no session cookies needed
        CorsConfiguration apiConfig = new CorsConfiguration();
        apiConfig.setAllowedOriginPatterns(List.of("*"));
        apiConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        apiConfig.setAllowedHeaders(List.of("*"));
        apiConfig.setAllowCredentials(false);
        apiConfig.setMaxAge(3600L);

        // Admin / merchant portals – restricted origins, credentials allowed
        CorsConfiguration adminConfig = new CorsConfiguration();
        adminConfig.setAllowedOrigins(Arrays.asList(allowedOrigins));
        adminConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        adminConfig.setAllowedHeaders(List.of("*"));
        adminConfig.setAllowCredentials(true);
        adminConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", apiConfig);
        source.registerCorsConfiguration("/**", adminConfig);
        return source;
    }
}
