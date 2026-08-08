package net.citotech.cito.billing.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import net.citotech.cito.billing.outbox.BillingOutboxEntry;
import net.citotech.cito.billing.outbox.OutboxEntryStatus;
import org.junit.jupiter.api.Test;

/** Covers {@link RatedChargeOutboxHandler}'s rate-then-persist path and its no-price-book no-op. */
class RatedChargeOutboxHandlerTest {

    @Test
    void supportsMatchesBothPaymentDirectionsOnly() {
        RatedChargeOutboxHandler handler =
                new RatedChargeOutboxHandler(
                        mock(RatingEngine.class), mock(RatedChargeRepository.class));

        assertThat(handler.supports("PAYMENT_COLLECTION_SUBMITTED")).isTrue();
        assertThat(handler.supports("PAYMENT_PAYOUT_SUBMITTED")).isTrue();
        assertThat(handler.supports("SOME_OTHER_EVENT")).isFalse();
    }

    @Test
    void handlePersistsARatedChargeWhenAPriceBookResolves() {
        RatingEngine ratingEngine = mock(RatingEngine.class);
        RatedChargeRepository ratedChargeRepository = mock(RatedChargeRepository.class);
        RatedCharge ratedCharge =
                new RatedCharge(1L, new BigDecimal("120.00"), "UGX", "HALF_UP_SCALE_2", "[]", "{}");
        Instant createdAt = Instant.parse("2026-08-08T10:00:00Z");
        when(ratingEngine.rate(
                        eq(7L),
                        eq("PAYMENT"),
                        eq("payment_event_count"),
                        eq("CUSTOMER_CHARGE"),
                        eq(new BigDecimal("1000")),
                        eq("UGX"),
                        eq(createdAt)))
                .thenReturn(Optional.of(ratedCharge));

        BillingOutboxEntry entry = entry(createdAt, "1000");
        new RatedChargeOutboxHandler(ratingEngine, ratedChargeRepository).handle(entry);

        verify(ratedChargeRepository)
                .insertIfAbsent(
                        eq(7L),
                        eq("PAYMENT"),
                        eq("payment_event_count"),
                        eq("CUSTOMER_CHARGE"),
                        eq("TX-1"),
                        eq(new BigDecimal("1000")),
                        eq(ratedCharge),
                        eq("rated:PAYMENT_COLLECTION_SUBMITTED:TX-1"));
    }

    @Test
    void handleIsANoOpWhenNoPriceBookResolvesYet() {
        RatingEngine ratingEngine = mock(RatingEngine.class);
        RatedChargeRepository ratedChargeRepository = mock(RatedChargeRepository.class);
        when(ratingEngine.rate(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        new RatedChargeOutboxHandler(ratingEngine, ratedChargeRepository)
                .handle(entry(Instant.now(), "1000"));

        verify(ratedChargeRepository, never())
                .insertIfAbsent(
                        anyLong(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        any(),
                        anyString());
    }

    private BillingOutboxEntry entry(Instant createdAt, String amount) {
        return new BillingOutboxEntry(
                1L,
                "PAYMENT",
                "TX-1",
                "PAYMENT_COLLECTION_SUBMITTED",
                Map.of(
                        "billingTenantId",
                        7,
                        "merchantId",
                        42,
                        "transactionReference",
                        "TX-1",
                        "amount",
                        amount,
                        "currency",
                        "UGX"),
                OutboxEntryStatus.PROCESSING,
                0,
                createdAt,
                null,
                createdAt,
                createdAt);
    }
}
