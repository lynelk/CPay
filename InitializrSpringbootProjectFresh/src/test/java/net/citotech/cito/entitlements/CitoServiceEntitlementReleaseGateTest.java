package net.citotech.cito.entitlements;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class CitoServiceEntitlementReleaseGateTest {
    private String source(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    @Test
    void platformCatalogEnumeratesEveryCurrentCitoProductModule() throws Exception {
        String migration = source("src/main/resources/db/migration/V87__cito_entitlements.sql");
        for (String service :
                List.of(
                        "CPAY",
                        "IDENTITY_VALIDATION",
                        "COMMUNICATIONS",
                        "VENDING",
                        "BILLING",
                        "MARKETPLACE_PAYMENTS",
                        "RECURRING_PAYMENTS",
                        "MERCHANT_ANALYTICS",
                        "DEVELOPER_CONTROL_PLANE",
                        "VIRTUAL_ACCOUNTS",
                        "EMBEDDED_CITO",
                        "INTEGRATIONS_MARKETPLACE")) {
            assertThat(migration).contains("'" + service + "'");
        }
    }

    @Test
    void productionAccessFailsClosedWithoutExplicitActiveEntitlement() throws Exception {
        String service =
                source(
                        "src/main/java/net/citotech/cito/entitlements/CitoServiceEntitlementService.java");
        assertThat(service)
                .contains("PRODUCTION")
                .contains("NO_ACTIVE_ENTITLEMENT")
                .contains("default_sandbox_access")
                .contains("ACTIVE_ENTITLEMENT")
                .contains("OUTSIDE_VALIDITY_WINDOW")
                .contains("SERVICE_ENTITLEMENT_DECISION");
    }

    @Test
    void baasCredentialAuthenticationCannotBypassBillingProductEntitlement() throws Exception {
        String apiKey =
                source(
                        "src/main/java/net/citotech/cito/billing/baas/BillingBaasApiKeyService.java");
        assertThat(apiKey)
                .contains("entitlementService.requireMerchantAccess(")
                .contains("\"BILLING\"")
                .contains("context.environment()")
                .contains("SERVICE_ACCOUNT:");
    }

    @Test
    void approvedProductionBaasTenantsAreMigratedIntoExplicitBillingEntitlement() throws Exception {
        String bridge =
                source(
                        "src/main/resources/db/migration/V108__billing_product_entitlement_bridge.sql");
        assertThat(bridge)
                .contains("'BILLING','PRODUCTION','ACTIVE'")
                .contains("legal_model_status='APPROVED'")
                .contains("commercial_model_status='APPROVED'")
                .contains("tax_model_status='APPROVED'")
                .contains("funds_flow_status='APPROVED'");
    }
}
