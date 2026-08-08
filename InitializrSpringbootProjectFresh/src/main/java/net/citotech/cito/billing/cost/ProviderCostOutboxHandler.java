package net.citotech.cito.billing.cost;

import java.math.BigDecimal;
import java.util.Map;
import net.citotech.cito.billing.outbox.BillingOutboxEntry;
import net.citotech.cito.billing.outbox.OutboxEventHandler;
import net.citotech.cito.billing.pricing.RatedChargeRepository;
import net.citotech.cito.billing.pricing.RatingEngine;
import org.springframework.stereotype.Component;

/**
 * The third {@code billing_outbox} consumer, alongside {@code UsageEventOutboxHandler} and {@code
 * RatedChargeOutboxHandler}: computes what CPay pays the provider for a submitted payment, using
 * the same {@link RatingEngine}/{@link RatedChargeRepository} infrastructure as the customer-charge
 * path (billing/pricing, Slices 19-21) but against {@code charge_type='PROVIDER_COST'} price-book
 * rows instead of {@code 'CUSTOMER_CHARGE'}. Cost and price are independently effective-dated for
 * free: they are simply different rows in the same {@code billing_price_book_versions}/{@code
 * billing_price_components} tables (V43), each with its own {@code effective_from}/{@code
 * effective_to}. A dedicated parallel cost table pair was considered and rejected - the existing
 * {@code charge_type} discriminator already gives cost its own independent versioning without
 * duplicating the rating engine or the price-book schema.
 *
 * <p>A no-op, not a failure, when no active {@code PROVIDER_COST} price book resolves yet -
 * matching {@code RatedChargeOutboxHandler}'s own reasoning for the customer-charge side.
 */
@Component
public class ProviderCostOutboxHandler implements OutboxEventHandler {
    private static final String PAYMENT_COLLECTION_SUBMITTED = "PAYMENT_COLLECTION_SUBMITTED";
    private static final String PAYMENT_PAYOUT_SUBMITTED = "PAYMENT_PAYOUT_SUBMITTED";
    private static final String SERVICE_CODE = "PAYMENT";
    private static final String METER_CODE = "payment_event_count";
    private static final String CHARGE_TYPE = "PROVIDER_COST";

    private final RatingEngine ratingEngine;
    private final RatedChargeRepository ratedChargeRepository;

    public ProviderCostOutboxHandler(
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
                        ratedCost ->
                                ratedChargeRepository.insertIfAbsent(
                                        billingTenantId,
                                        SERVICE_CODE,
                                        METER_CODE,
                                        CHARGE_TYPE,
                                        transactionReference,
                                        baseAmount,
                                        ratedCost,
                                        "rated-cost:"
                                                + entry.eventType()
                                                + ":"
                                                + transactionReference));
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException(
                "Expected a numeric billingTenantId in the outbox payload, got: " + value);
    }
}
