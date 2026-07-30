package net.citotech.cito.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.mock.env.MockEnvironment;

class ProductionSafetyConfigTest {

    @Test
    void productionProfileRejectsSandboxGatewayState() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");

        ApplicationRunner guard = new ProductionSafetyConfig()
            .productionSafetyGuard(environment, "SANDBOX", "jdbc");

        assertThatThrownBy(() -> guard.run(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("custom.gatewaystate=PRODUCTION");
    }

    @Test
    void nonProductionProfilesAllowSandboxSettings() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        ApplicationRunner guard = new ProductionSafetyConfig()
            .productionSafetyGuard(environment, "SANDBOX", "memory");

        assertThatCode(() -> guard.run(null)).doesNotThrowAnyException();
    }

    @Test
    void productionProfileRejectsMemoryNonceStore() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");

        ApplicationRunner guard = new ProductionSafetyConfig()
            .productionSafetyGuard(environment, "PRODUCTION", "memory");

        assertThatThrownBy(() -> guard.run(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("durable cpay.security.nonce-store");
    }
}
