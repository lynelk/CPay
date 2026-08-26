package net.citotech.cito.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.mock.env.MockEnvironment;

class ProductionSafetyConfigTest {

    private static final String STRONG_ACTUATOR_PASSWORD = "actuator-9Jv!xP2mQ7sL";
    private static final String STRONG_ADMIN_PASSWORD = "admin-4Nz!rK8vT1pWx";
    private static final String STRONG_CALLBACK_SECRET =
            "callback-signing-secret-9Jv-xP2m-Q7sL-2026";
    private static final String STRONG_ENCRYPTION_KEY =
            "merchant-encryption-key-4Nz-rK8v-T1pW-2026";

    @Test
    void productionProfileRejectsSandboxGatewayState() {
        MockEnvironment environment = productionEnvironment();

        ApplicationRunner guard =
                guard(environment, "SANDBOX", "jdbc", "https://cito.example.com", "https://cito.example.com");

        assertThatThrownBy(() -> guard.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("custom.gatewaystate=PRODUCTION");
    }

    @Test
    void nonProductionProfilesAllowSandboxSettings() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        ApplicationRunner guard =
                new ProductionSafetyConfig()
                        .productionSafetyGuard(
                                environment,
                                "SANDBOX",
                                "memory",
                                "http://localhost:8081",
                                "",
                                "",
                                "",
                                "",
                                "",
                                "",
                                "");

        assertThatCode(() -> guard.run(null)).doesNotThrowAnyException();
    }

    @Test
    void productionProfileRejectsMemoryNonceStore() {
        MockEnvironment environment = productionEnvironment();

        ApplicationRunner guard =
                guard(environment, "PRODUCTION", "memory", "https://cito.example.com", "https://cito.example.com");

        assertThatThrownBy(() -> guard.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("durable cpay.security.nonce-store");
    }

    @Test
    void productionProfileRejectsPlainHttpBaseUrl() {
        MockEnvironment environment = productionEnvironment();

        ApplicationRunner guard =
                guard(environment, "PRODUCTION", "jdbc", "http://cito.example.com", "https://cito.example.com");

        assertThatThrownBy(() -> guard.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_BASE_URL");
    }

    @Test
    void productionProfileRejectsWildcardOrLoopbackCors() {
        MockEnvironment environment = productionEnvironment();

        ApplicationRunner wildcardGuard =
                guard(environment, "PRODUCTION", "jdbc", "https://cito.example.com", "*");
        ApplicationRunner loopbackGuard =
                guard(
                        environment,
                        "PRODUCTION",
                        "jdbc",
                        "https://cito.example.com",
                        "https://localhost:3000");

        assertThatThrownBy(() -> wildcardGuard.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS");
        assertThatThrownBy(() -> loopbackGuard.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Loopback");
    }

    @Test
    void productionProfileAcceptsExplicitStrongConfiguration() {
        MockEnvironment environment = productionEnvironment();

        ApplicationRunner guard =
                guard(
                        environment,
                        "PRODUCTION",
                        "jdbc",
                        "https://cito.example.com",
                        "https://cito.example.com,https://bo.cito.example.com");

        assertThatCode(() -> guard.run(null)).doesNotThrowAnyException();
    }

    private ApplicationRunner guard(
            MockEnvironment environment,
            String gatewayState,
            String nonceStore,
            String appBaseUrl,
            String origins) {
        return new ProductionSafetyConfig()
                .productionSafetyGuard(
                        environment,
                        gatewayState,
                        nonceStore,
                        appBaseUrl,
                        origins,
                        "actuator-ops",
                        STRONG_ACTUATOR_PASSWORD,
                        "cito-admin-api",
                        STRONG_ADMIN_PASSWORD,
                        STRONG_CALLBACK_SECRET,
                        STRONG_ENCRYPTION_KEY);
    }

    private MockEnvironment productionEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");
        return environment;
    }
}
