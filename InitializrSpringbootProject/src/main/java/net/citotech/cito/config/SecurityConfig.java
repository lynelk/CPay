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

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:2019}")
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
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors().and()
            .csrf().disable()
            .authorizeHttpRequests(auth -> auth
                .antMatchers("/api/v2/admin/**").hasRole("ADMIN")
                .antMatchers("/actuator/**").hasRole("ACTUATOR")
                .anyRequest().permitAll()
            )
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.ALWAYS)
            .and()
            .httpBasic();
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        validateCredentials(actuatorUsername, actuatorPassword, "ACTUATOR_USERNAME and ACTUATOR_PASSWORD must be set");
        validateCredentials(adminUsername, adminPassword, "ADMIN_API_USERNAME and ADMIN_API_PASSWORD must be set");
        UserDetails actuatorUser = User.withUsername(actuatorUsername).password(encoder.encode(actuatorPassword)).roles("ACTUATOR").build();
        UserDetails adminUser = User.withUsername(adminUsername).password(encoder.encode(adminPassword)).roles("ADMIN").build();
        return new InMemoryUserDetailsManager(actuatorUser, adminUser);
    }

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration trustedConfig = new CorsConfiguration();
        trustedConfig.setAllowedOrigins(Arrays.asList(allowedOrigins));
        trustedConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        trustedConfig.setAllowedHeaders(List.of("*"));
        trustedConfig.setAllowCredentials(true);
        trustedConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", trustedConfig);
        source.registerCorsConfiguration("/**", trustedConfig);
        return source;
    }

    private void validateCredentials(String username, String password, String message) {
        if (isBlank(username) || isBlank(password)) throw new IllegalStateException(message);
    }

    private boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
}
