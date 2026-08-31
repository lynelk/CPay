package net.citotech.cito.billing.usage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UsageTenantIsolationReleaseGateTest {
    private String source(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    @Test
    void usageIdempotencyIsUniqueWithinTenantNotGlobally() throws Exception {
        String migration =
                source("src/main/resources/db/migration/V107__billing_usage_tenant_idempotency.sql");
        assertThat(migration)
                .contains("DROP INDEX `uk_billing_usage_event_idempotency`")
                .contains("(`billing_tenant_id`,`idempotency_key`)");
    }

    @Test
    void repositoryReplayLookupAndQueriesCarryTenantPredicate() throws Exception {
        String repository =
                source("src/main/java/net/citotech/cito/billing/usage/UsageEventRepository.java");
        assertThat(repository)
                .contains("findByIdempotencyKey(long billingTenantId, String idempotencyKey)")
                .contains("WHERE billing_tenant_id=:billing_tenant_id")
                .contains("WHERE billing_tenant_id=:tenant AND event_time>=:from")
                .contains("Usage idempotency key was already used with different event attributes");
    }

    @Test
    void publicBaasUsageChecksResolvedTenantMeterDimensionsAndQuota() throws Exception {
        String service =
                source("src/main/java/net/citotech/cito/billing/baas/BillingBaasUsageService.java");
        assertThat(service)
                .contains("event.billingTenantId() != context.billingTenantId()")
                .contains("validateMeter(service, meter, time, dims)")
                .contains("usage_events_per_day")
                .contains("unsupported meter dimensions")
                .contains("billing_tenant_id=:tenant");
    }
}
