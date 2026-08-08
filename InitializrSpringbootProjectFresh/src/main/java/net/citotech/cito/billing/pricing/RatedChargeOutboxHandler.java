package net.citotech.cito.billing.pricing;

import java.math.BigDecimal;
import java.util.Map;
import net.citotech.cito.billing.outbox.BillingOutboxEntry;
import net.citotech.cito.billing.outbox.OutboxEventHandler;
import org.springframework.stereotype.Component;

/**
 * The second real {@code billing_outbox} consumer, running alongside {@code
 * UsageEventOutboxHandler} on the same {@code PAYMENT_COLLECTION_SUBMITTED}/{@code
 * PAYMENT_PAYOUT_SUBMITTED} entries (Slice 17's fan-out): computes a rated charge via {@link
 * RatingEngine} and persists it via {@link RatedChargeRepository}. A no-op, not a failure, when no
 * active price book resolves for the tenant/service/meter/chargeType yet - most merchants have no
 * price book configured in this phase, and treating that as a failure would leave every payment's
 * outbox entry retrying forever until parked {@code FAILED}, even though nothing is actually wrong.
 */
@Component
public class RatedChargeOutboxHandler implements OutboxEventHandler {
    private static final String PAYMENT_COLLECTION_SUBMITTED = "PAYMENT_COLLECTION_SUBMITTED";
    private static final String PAYMENT_PAYOUT_SUBMITTED = "PAYMENT_PAYOUT_SUBMITTED";
    private static final String SERVICE_CODE = "PAYMENT";
    private static final String METER_CODE = "payment_event_count";
    private static final String CHARGE_TYPE = "CUSTOMER_CHARGE";

    private final RatingEngine ratingEngine;
    private final RatedChargeRepository ratedChargeRepository;

    public RatedChargeOutboxHandler(
            RatingEngine ratingEngine, RatedChargeRepository ratedChargeRepository) {
        this.ratingEngine = ratingEngine;
        this.ratedChargeRepository = ratedChargeRepository;
    }

    @Override
    public boolean supports(String eventType) {
        return PAYMENT_COLLECTION_SUBMITTED.equals(eventType)
                || PAYMENT_PAYOUT_SUBMITTED.equals(eventType);
    }

    @Override
    public void handle(BillingOutboxEntry entry) {
        Map<String, Object> payload = entry.payload();
        long billingTenantId = asLong(payload.get("billingTenantId"));
        String transactionReference = (String) payload.get("transactionReference");
        BigDecimal baseAmount = new BigDecimal(String.valueOf(payload.get("amount")));
        String currency = (String) payload.get("currency");

        ratingEngine
                .rate(
                        billingTenantId,
                        SERVICE_CODE,
                        METER_CODE,
                        CHARGE_TYPE,
                        baseAmount,
                        currency,
                        entry.createdAt())
                .ifPresent(
                        ratedCharge ->
                                ratedChargeRepository.insertIfAbsent(
                                        billingTenantId,
                                        SERVICE_CODE,
                                        METER_CODE,
                                        CHARGE_TYPE,
                                        transactionReference,
                                        baseAmount,
                                        ratedCharge,
                                        "rated:" + entry.eventType() + ":" + transactionReference));
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException(
                "Expected a numeric billingTenantId in the outbox payload, got: " + value);
    }
}
