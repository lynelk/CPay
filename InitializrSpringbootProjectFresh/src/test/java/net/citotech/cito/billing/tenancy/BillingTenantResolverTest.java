package net.citotech.cito.billing.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;

/**
 * Covers ADR 0003: {@code merchantId -> billing_tenant_id} resolution, and the failure mode for a
 * merchant with no {@code billing_tenants} row (should never happen after the {@code V38} backfill,
 * but must fail loudly rather than silently if it ever does).
 */
class BillingTenantResolverTest {

    @Test
    void resolveTenantIdReturnsTheMappedTenantId() {
        BillingTenantRepository repository = mock(BillingTenantRepository.class);
        BillingTenant tenant = new BillingTenant(7L, 42L, "CPAY_MERCHANT", "ACTIVE", Instant.now());
        when(repository.findByMerchantId(42L)).thenReturn(Optional.of(tenant));

        long tenantId = new BillingTenantResolver(repository).resolveTenantId(42L);

        assertThat(tenantId).isEqualTo(7L);
    }

    @Test
    void resolveTenantReturnsTheFullTenantRecord() {
        BillingTenantRepository repository = mock(BillingTenantRepository.class);
        BillingTenant tenant = new BillingTenant(7L, 42L, "CPAY_MERCHANT", "ACTIVE", Instant.now());
        when(repository.findByMerchantId(42L)).thenReturn(Optional.of(tenant));

        BillingTenant resolved = new BillingTenantResolver(repository).resolveTenant(42L);

        assertThat(resolved).isEqualTo(tenant);
    }

    @Test
    void resolveTenantIdThrowsForAnUnmappedMerchant() {
        BillingTenantRepository repository = mock(BillingTenantRepository.class);
        when(repository.findByMerchantId(99L)).thenReturn(Optional.empty());
        BillingTenantResolver resolver = new BillingTenantResolver(repository);

        assertThatThrownBy(() -> resolver.resolveTenantId(99L))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("99");
    }
}
