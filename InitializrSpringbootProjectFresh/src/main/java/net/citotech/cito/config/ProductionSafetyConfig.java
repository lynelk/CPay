package net.citotech.cito.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class ProductionSafetyConfig {

    @Bean
    ApplicationRunner productionSafetyGuard(
            Environment environment,
            @Value("${custom.gatewaystate:SANDBOX}") String gatewayState,
            @Value("${cpay.security.nonce-store:jdbc}") String nonceStore) {
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
        };
    }

    private boolean isProductionProfile(Environment environment) {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(
                        profile ->
                                "prod".equalsIgnoreCase(profile)
                                        || "production".equalsIgnoreCase(profile));
    }
}
