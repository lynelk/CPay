package net.citotech.cito.billing.baas;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Release-level structural guard for the most security-sensitive BaaS paths. Behavioural integration
 * tests remain required, but these assertions prevent accidental removal of the tenant predicates
 * and credential-derived context that every lower-level test depends on.
 */
class BillingTenantIsolationReleaseGateTest {

    @Test
    void baasTenantComesFromAuthenticatedCredentialMappingNotRequestBody() throws Exception {
        String apiKeyService =
                Files.readString(
                        Path.of(
                                "src/main/java/net/citotech/cito/billing/baas/BillingBaasApiKeyService.java"),
                        StandardCharsets.UTF_8);
        String controller =
                Files.readString(
                        Path.of(
                                "src/main/java/net/citotech/cito/billing/baas/BillingBaasController.java"),
                        StandardCharsets.UTF_8);

        assertThat(apiKeyService)
                .contains("JOIN billing_tenant_developer_projects")
                .contains("bt.id AS billing_tenant_id")
                .contains("BaaS credential resolves to multiple billing tenants");
        assertThat(controller)
                .doesNotContain("record CustomerRequest(\n            Long billingTenantId")
                .doesNotContain("record AccountRequest(\n            Long billingTenantId")
                .doesNotContain("record AuthorizeChargeRequest(\n            Long billingTenantId");
    }

    @Test
    void chargingReadsAndMutationsStayTenantScoped() throws Exception {
        String charging =
                Files.readString(
                        Path.of(
                                "src/main/java/net/citotech/cito/billing/baas/BillingBaasChargingService.java"),
                        StandardCharsets.UTF_8);

        assertThat(charging)
                .contains("WHERE r.billing_tenant_id=:tenant AND r.reservation_reference=:reservation")
                .contains("WHERE billing_tenant_id=:tenant AND idempotency_key=:idempotency")
                .contains("WHERE id=:account AND billing_tenant_id=:tenant")
                .contains("g.billing_tenant_id=:tenant")
                .contains("billing_customer_id=:customer");
    }

    @Test
    void focusExportsCannotCrossTenantBoundary() throws Exception {
        String focus =
                Files.readString(
                        Path.of(
                                "src/main/java/net/citotech/cito/billing/export/FocusExportService.java"),
                        StandardCharsets.UTF_8);

        assertThat(focus)
                .contains("ue.billing_tenant_id=:tenant")
                .contains("cc.billing_tenant_id=ue.billing_tenant_id")
                .contains("pc.billing_tenant_id=ue.billing_tenant_id");
    }
}
