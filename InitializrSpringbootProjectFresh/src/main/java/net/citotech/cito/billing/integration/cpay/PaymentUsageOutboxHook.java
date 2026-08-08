package net.citotech.cito.billing.integration.cpay;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.Model.Transaction;
import net.citotech.cito.admin.FeatureRegistryService;
import net.citotech.cito.api.v2.dto.PaymentRequest;
import net.citotech.cito.billing.outbox.OutboxWriter;
import net.citotech.cito.billing.tenancy.BillingTenantResolver;
import org.springframework.stereotype.Service;

/**
 * The single hook point where CPay's payment path records a {@code billing_outbox} entry (ADR
 * 0005), gated by the {@code billing-usage-outbox} feature flag (global default off, V42; a
 * per-merchant {@code merchant_feature_flags} override can opt a merchant in early via {@link
 * FeatureRegistryService}). Called from {@code PaymentOrchestrationService.collect()} (after {@code
 * Common.doPayIn} succeeds) and {@code payout()} (after the ledger reservation is captured and
 * before it can be released) - like {@code queueWebhook} in that same class, neither call ever
 * throws: the payment/payout result is authoritative and must never be rolled back or fail because
 * of this shadow write.
 */
@Service
public class PaymentUsageOutboxHook {
    private static final String FLAG_KEY = "billing-usage-outbox";
    private static final Logger logger = Logger.getLogger(PaymentUsageOutboxHook.class.getName());

    private final FeatureRegistryService featureRegistry;
    private final BillingTenantResolver tenantResolver;
    private final OutboxWriter outboxWriter;

    public PaymentUsageOutboxHook(
            FeatureRegistryService featureRegistry,
            BillingTenantResolver tenantResolver,
            OutboxWriter outboxWriter) {
        this.featureRegistry = featureRegistry;
        this.tenantResolver = tenantResolver;
        this.outboxWriter = outboxWriter;
    }

    public void recordPaymentCollected(Merchant merchant, PaymentRequest request, Transaction tx) {
        recordSubmitted(merchant, request, tx, "PAYMENT_COLLECTION_SUBMITTED");
    }

    public void recordPaymentPayoutSubmitted(
            Merchant merchant, PaymentRequest request, Transaction tx) {
        recordSubmitted(merchant, request, tx, "PAYMENT_PAYOUT_SUBMITTED");
    }

    private void recordSubmitted(
            Merchant merchant, PaymentRequest request, Transaction tx, String eventType) {
        if (!featureRegistry.isEnabled(FLAG_KEY, merchant.getId())) {
            return;
        }
        try {
            long billingTenantId = tenantResolver.resolveTenantId(merchant.getId());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("billingTenantId", billingTenantId);
            payload.put("merchantId", merchant.getId());
            payload.put("transactionReference", tx.getTx_unique_id());
            payload.put("amount", request.getAmount());
            payload.put("currency", request.getCurrency());
            outboxWriter.write("PAYMENT", tx.getTx_unique_id(), eventType, payload);
        } catch (Exception ex) {
            logger.log(
                    Level.WARNING,
                    "Billing usage outbox write failed for tx "
                            + tx.getTx_unique_id()
                            + ": "
                            + ex.getMessage(),
                    ex);
        }
    }
}
