package net.citotech.cito.config;

import java.net.URI;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Production-only fail-closed checks for configuration that would materially weaken Cito's
 * security posture. Local and sandbox profiles remain developer-friendly; production must be
 * explicit about trust boundaries and secrets.
 */
@Configuration
public class ProductionSafetyConfig {

    private static final int MIN_OPERATOR_PASSWORD_LENGTH = 16;
    private static final int MIN_SIGNING_SECRET_LENGTH = 32;

    @Bean
    ApplicationRunner productionSafetyGuard(
            Environment environment,
            @Value("${custom.gatewaystate:SANDBOX}") String gatewayState,
            @Value("${cpay.security.nonce-store:jdbc}") String nonceStore,
            @Value("${app.base.url:http://localhost:8081}") String appBaseUrl,
            @Value("${cors.allowed-origins:}") String allowedOrigins,
            @Value("${actuator.username:}") String actuatorUsername,
            @Value("${actuator.password:}") String actuatorPassword,
            @Value("${admin.api.username:}") String adminUsername,
            @Value("${admin.api.password:}") String adminPassword,
            @Value("${callback.signing.secret:}") String callbackSigningSecret,
            @Value("${merchant.channel.encryption.key:}") String merchantEncryptionKey) {
        return args -> {
            if (!isProductionProfile(environment)) {
                return;
            }
            if (!"PRODUCTION".equalsIgnoreCase(gatewayState)) {
                throw new IllegalStateException(
                        "Production profiles require custom.gatewaystate=PRODUCTION");
            }
            if ("memory".equalsIgnoreCase(nonceStore)) {
                throw new IllegalStateException(
                        "Production profiles require a durable cpay.security.nonce-store");
            }

            requireHttpsUrl(appBaseUrl, "APP_BASE_URL must be an absolute HTTPS URL in production");
            validateProductionOrigins(allowedOrigins);

            requireStrongCredential(
                    actuatorUsername,
                    actuatorPassword,
                    "ACTUATOR_PASSWORD must be at least "
                            + MIN_OPERATOR_PASSWORD_LENGTH
                            + " characters in production");
            requireStrongCredential(
                    adminUsername,
                    adminPassword,
                    "ADMIN_API_PASSWORD must be at least "
                            + MIN_OPERATOR_PASSWORD_LENGTH
                            + " characters in production");

            if (actuatorUsername.equalsIgnoreCase(adminUsername)) {
                throw new IllegalStateException(
                        "Production requires distinct actuator and admin API identities");
            }
            if (actuatorPassword.equals(adminPassword)) {
                throw new IllegalStateException(
                        "Production requires distinct actuator and admin API credentials");
            }

            requireSecret(
                    callbackSigningSecret,
                    MIN_SIGNING_SECRET_LENGTH,
                    "CALLBACK_SIGNING_SECRET must be at least 32 characters in production");
            requireSecret(
                    merchantEncryptionKey,
                    MIN_SIGNING_SECRET_LENGTH,
                    "MERCHANT_CHANNEL_ENCRYPTION_KEY must be at least 32 characters in production");
        };
    }

    private void validateProductionOrigins(String allowedOrigins) {
        if (isBlank(allowedOrigins)) {
            throw new IllegalStateException(
                    "CORS_ALLOWED_ORIGINS/cors.allowed-origins must be explicitly configured in production");
        }
        for (String origin : allowedOrigins.split(",")) {
            String trimmed = origin.trim();
            if (trimmed.isEmpty() || "*".equals(trimmed)) {
                throw new IllegalStateException("Production CORS origins must be explicit HTTPS origins");
            }
            requireHttpsUrl(trimmed, "Production CORS origins must use HTTPS: " + trimmed);
            URI uri = URI.create(trimmed);
            String host = uri.getHost();
            if (host == null
                    || "localhost".equalsIgnoreCase(host)
                    || "127.0.0.1".equals(host)
                    || "::1".equals(host)) {
                throw new IllegalStateException(
                        "Loopback CORS origins are not permitted in production: " + trimmed);
            }
        }
    }

    private void requireHttpsUrl(String value, String message) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalStateException(message);
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(message, e);
        }
    }

    private void requireStrongCredential(String username, String password, String message) {
        if (isBlank(username)
                || isBlank(password)
                || password.length() < MIN_OPERATOR_PASSWORD_LENGTH
                || password.equalsIgnoreCase(username)
                || isObviousPlaceholder(password)) {
            throw new IllegalStateException(message);
        }
    }

    private void requireSecret(String value, int minimumLength, String message) {
        if (isBlank(value) || value.length() < minimumLength || isObviousPlaceholder(value)) {
            throw new IllegalStateException(message);
        }
    }

    private boolean isObviousPlaceholder(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        return normalized.contains("changeme")
                || normalized.contains("change-me")
                || normalized.contains("password")
                || normalized.contains("secret123")
                || normalized.equals("admin")
                || normalized.equals("test")
                || normalized.equals("default");
    }

    private boolean isProductionProfile(Environment environment) {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(
                        profile ->
                                "prod".equalsIgnoreCase(profile)
                                        || "production".equalsIgnoreCase(profile));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
