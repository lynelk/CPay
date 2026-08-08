package net.citotech.cito.billing.integration.cpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.Model.Transaction;
import net.citotech.cito.admin.FeatureRegistryService;
import net.citotech.cito.api.v2.dto.PaymentRequest;
import net.citotech.cito.billing.outbox.OutboxWriter;
import net.citotech.cito.billing.tenancy.BillingTenantResolver;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Covers the {@code billing-usage-outbox} feature-flag gate and the never-throws contract that lets
 * {@code PaymentOrchestrationService.collect()} call this unconditionally after a successful {@code
 * Common.doPayIn} without risking the payment result.
 */
class PaymentUsageOutboxHookTest {

    @Test
    void recordPaymentCollectedIsANoOpWhenTheFlagIsOff() {
        FeatureRegistryService featureRegistry = mock(FeatureRegistryService.class);
        BillingTenantResolver tenantResolver = mock(BillingTenantResolver.class);
        OutboxWriter outboxWriter = mock(OutboxWriter.class);
        when(featureRegistry.isEnabled(eq("billing-usage-outbox"), eq(42L))).thenReturn(false);

        new PaymentUsageOutboxHook(featureRegistry, tenantResolver, outboxWriter)
                .recordPaymentCollected(merchant(42L), request(), transaction());

        verify(outboxWriter, never()).write(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordPaymentCollectedWritesAnOutboxEntryWhenTheFlagIsOn() {
        FeatureRegistryService featureRegistry = mock(FeatureRegistryService.class);
        BillingTenantResolver tenantResolver = mock(BillingTenantResolver.class);
        OutboxWriter outboxWriter = mock(OutboxWriter.class);
        when(featureRegistry.isEnabled(eq("billing-usage-outbox"), eq(42L))).thenReturn(true);
        when(tenantResolver.resolveTenantId(42L)).thenReturn(7L);

        new PaymentUsageOutboxHook(featureRegistry, tenantResolver, outboxWriter)
                .recordPaymentCollected(merchant(42L), request(), transaction());

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(outboxWriter)
                .write(
                        eq("PAYMENT"),
                        eq("TX-1"),
                        eq("PAYMENT_COLLECTION_SUBMITTED"),
                        payloadCaptor.capture());
        assertThat(payloadCaptor.getValue())
                .containsEntry("billingTenantId", 7L)
                .containsEntry("merchantId", 42L)
                .containsEntry("transactionReference", "TX-1")
                .containsEntry("amount", "1000")
                .containsEntry("currency", "UGX");
    }

    @Test
    void recordPaymentCollectedSwallowsAnUnmappedBillingTenant() {
        FeatureRegistryService featureRegistry = mock(FeatureRegistryService.class);
        BillingTenantResolver tenantResolver = mock(BillingTenantResolver.class);
        OutboxWriter outboxWriter = mock(OutboxWriter.class);
        when(featureRegistry.isEnabled(eq("billing-usage-outbox"), eq(42L))).thenReturn(true);
        when(tenantResolver.resolveTenantId(42L))
                .thenThrow(
                        new PaymentGatewayException("No billing tenant is mapped for merchant 42"));

        new PaymentUsageOutboxHook(featureRegistry, tenantResolver, outboxWriter)
                .recordPaymentCollected(merchant(42L), request(), transaction());

        verify(outboxWriter, never()).write(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void recordPaymentCollectedSwallowsAnOutboxWriteFailure() {
        FeatureRegistryService featureRegistry = mock(FeatureRegistryService.class);
        BillingTenantResolver tenantResolver = mock(BillingTenantResolver.class);
        OutboxWriter outboxWriter = mock(OutboxWriter.class);
        when(featureRegistry.isEnabled(eq("billing-usage-outbox"), eq(42L))).thenReturn(true);
        when(tenantResolver.resolveTenantId(42L)).thenReturn(7L);
        when(outboxWriter.write(anyString(), anyString(), anyString(), anyMap()))
                .thenThrow(new RuntimeException("db down"));

        new PaymentUsageOutboxHook(featureRegistry, tenantResolver, outboxWriter)
                .recordPaymentCollected(merchant(42L), request(), transaction());
    }

    private Merchant merchant(long id) {
        Merchant merchant = new Merchant();
        merchant.setId(id);
        return merchant;
    }

    private PaymentRequest request() {
        PaymentRequest request = new PaymentRequest();
        request.setAmount("1000");
        request.setCurrency("UGX");
        return request;
    }

    private Transaction transaction() {
        Transaction tx = new Transaction();
        tx.setTx_unique_id("TX-1");
        return tx;
    }
}
