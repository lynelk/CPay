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
            @Value("${custom.ssl.skip-verify:false}") boolean skipSslVerification) {
        return args -> {
            if (!isProductionProfile(environment)) {
                return;
            }
            if (!"PRODUCTION".equalsIgnoreCase(gatewayState)) {
                throw new IllegalStateException("Production profiles require custom.gatewaystate=PRODUCTION");
            }
            if (skipSslVerification) {
                throw new IllegalStateException("Production profiles cannot run with custom.ssl.skip-verify=true");
            }
        };
    }

    private boolean isProductionProfile(Environment environment) {
        return Arrays.stream(environment.getActiveProfiles())
            .anyMatch(profile -> "prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile));
    }
}
