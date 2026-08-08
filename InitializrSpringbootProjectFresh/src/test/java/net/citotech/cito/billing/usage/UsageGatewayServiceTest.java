package net.citotech.cito.billing.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import net.citotech.cito.billing.tenancy.BillingTenantResolver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Covers {@link UsageGatewayService}'s merchantId -> billing_tenant_id resolution and dedup
 * pass-through.
 */
class UsageGatewayServiceTest {

    @Test
    void recordUsageResolvesTheBillingTenantAndDelegatesToTheRepository() {
        BillingTenantResolver tenantResolver = mock(BillingTenantResolver.class);
        UsageEventRepository repository = mock(UsageEventRepository.class);
        when(tenantResolver.resolveTenantId(42L)).thenReturn(7L);
        UsageEvent persisted =
                new UsageEvent(
                        9L,
                        7L,
                        "PAYMENT",
                        "payment_event_count",
                        Instant.now(),
                        BigDecimal.ONE,
                        "UGX",
                        Map.of(),
                        "tx-123",
                        "key-1",
                        Instant.now());
        when(repository.insertIfAbsent(any())).thenReturn(persisted);

        UsageGatewayService service = new UsageGatewayService(tenantResolver, repository);
        UsageEvent result =
                service.recordUsage(
                        42L,
                        "PAYMENT",
                        "payment_event_count",
                        Instant.now(),
                        BigDecimal.ONE,
                        "UGX",
                        Map.of(),
                        "tx-123",
                        "key-1");

        assertThat(result).isSameAs(persisted);
        ArgumentCaptor<UsageEvent> captor = ArgumentCaptor.forClass(UsageEvent.class);
        verify(repository).insertIfAbsent(captor.capture());
        assertThat(captor.getValue().billingTenantId()).isEqualTo(7L);
        assertThat(captor.getValue().idempotencyKey()).isEqualTo("key-1");
    }
}
