package net.citotech.cito.billing.usage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import net.citotech.cito.billing.tenancy.BillingTenantResolver;
import org.springframework.stereotype.Service;

/**
 * Entry point every future usage producer (payments, SMS, webhooks, invoicing) calls to record one
 * meterable event. Resolves the caller's {@code merchantId} to a {@code billing_tenant_id} (ADR
 * 0003) and dedupes by {@code idempotencyKey} via {@link UsageEventRepository#insertIfAbsent} so a
 * retried caller (e.g. a redelivered provider callback) never double-counts. No producer calls this
 * yet - wiring a real caller (starting with {@code PaymentOrchestrationService.collect()}) is a
 * later, separately-flagged slice.
 */
@Service
public class UsageGatewayService {
    private final BillingTenantResolver tenantResolver;
    private final UsageEventRepository repository;

    public UsageGatewayService(
            BillingTenantResolver tenantResolver, UsageEventRepository repository) {
        this.tenantResolver = tenantResolver;
        this.repository = repository;
    }

    public UsageEvent recordUsage(
            long merchantId,
            String serviceCode,
            String meterCode,
            Instant eventTime,
            BigDecimal quantity,
            String currency,
            Map<String, String> dimensions,
            String sourceReference,
            String idempotencyKey) {
        long billingTenantId = tenantResolver.resolveTenantId(merchantId);
        UsageEvent event =
                new UsageEvent(
                        0L,
                        billingTenantId,
                        serviceCode,
                        meterCode,
                        eventTime,
                        quantity,
                        currency,
                        dimensions,
                        sourceReference,
                        idempotencyKey,
                        null);
        return repository.insertIfAbsent(event);
    }
}
