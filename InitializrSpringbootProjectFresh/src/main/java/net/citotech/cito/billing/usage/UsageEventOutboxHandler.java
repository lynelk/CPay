package net.citotech.cito.billing.usage;

import java.math.BigDecimal;
import java.util.Map;
import net.citotech.cito.billing.outbox.BillingOutboxEntry;
import net.citotech.cito.billing.outbox.OutboxEventHandler;
import org.springframework.stereotype.Component;

/**
 * The first real consumer of {@code billing_outbox} entries - the gap Phase 1 deliberately left
 * open. Turns a {@code PAYMENT_COLLECTION_SUBMITTED} entry into a {@code billing_usage_events} row
 * via {@link UsageGatewayService}, which is what makes {@code MeterAggregationService} and {@code
 * UsagePaymentReconciliationService} produce real numbers for the first time. {@code
 * PAYMENT_PAYOUT_SUBMITTED} support lands alongside the payout outbox hook itself (Slice 18).
 */
@Component
public class UsageEventOutboxHandler implements OutboxEventHandler {
    private static final String PAYMENT_COLLECTION_SUBMITTED = "PAYMENT_COLLECTION_SUBMITTED";

    private final UsageGatewayService usageGatewayService;

    public UsageEventOutboxHandler(UsageGatewayService usageGatewayService) {
        this.usageGatewayService = usageGatewayService;
    }

    @Override
    public boolean supports(String eventType) {
        return PAYMENT_COLLECTION_SUBMITTED.equals(eventType);
    }

    @Override
    public void handle(BillingOutboxEntry entry) {
        Map<String, Object> payload = entry.payload();
        long merchantId = asLong(payload.get("merchantId"));
        String transactionReference = (String) payload.get("transactionReference");
        String currency = (String) payload.get("currency");

        usageGatewayService.recordUsage(
                merchantId,
                "PAYMENT",
                "payment_event_count",
                entry.createdAt(),
                BigDecimal.ONE,
                currency,
                Map.of(),
                transactionReference,
                entry.eventType() + ":" + transactionReference);
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException(
                "Expected a numeric merchantId in the outbox payload, got: " + value);
    }
}
