package net.citotech.cito.billing.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import net.citotech.cito.billing.outbox.BillingOutboxEntry;
import net.citotech.cito.billing.outbox.OutboxEntryStatus;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link UsageEventOutboxHandler} - the Phase 2 handler that finally consumes {@code
 * billing_outbox} entries into {@code billing_usage_events}.
 */
class UsageEventOutboxHandlerTest {

    @Test
    void supportsOnlyMatchesPaymentCollectionSubmitted() {
        UsageEventOutboxHandler handler =
                new UsageEventOutboxHandler(mock(UsageGatewayService.class));

        assertThat(handler.supports("PAYMENT_COLLECTION_SUBMITTED")).isTrue();
        assertThat(handler.supports("PAYMENT_PAYOUT_SUBMITTED")).isFalse();
        assertThat(handler.supports("SOME_OTHER_EVENT")).isFalse();
    }

    @Test
    void handleRecordsAUsageEventFromThePayload() {
        UsageGatewayService usageGatewayService = mock(UsageGatewayService.class);
        Instant createdAt = Instant.parse("2026-08-08T10:00:00Z");
        // merchantId deserializes as Integer after a JSON round-trip through the outbox payload -
        // this must not be cast directly to Long.
        BillingOutboxEntry entry =
                new BillingOutboxEntry(
                        1L,
                        "PAYMENT",
                        "TX-1",
                        "PAYMENT_COLLECTION_SUBMITTED",
                        Map.of(
                                "billingTenantId", 7,
                                "merchantId", 42,
                                "transactionReference", "TX-1",
                                "amount", "1000",
                                "currency", "UGX"),
                        OutboxEntryStatus.PROCESSING,
                        0,
                        createdAt,
                        null,
                        createdAt,
                        createdAt);

        new UsageEventOutboxHandler(usageGatewayService).handle(entry);

        verify(usageGatewayService)
                .recordUsage(
                        eq(42L),
                        eq("PAYMENT"),
                        eq("payment_event_count"),
                        eq(createdAt),
                        eq(BigDecimal.ONE),
                        eq("UGX"),
                        anyMap(),
                        eq("TX-1"),
                        eq("PAYMENT_COLLECTION_SUBMITTED:TX-1"));
    }

    @Test
    void handleAcceptsALongMerchantId() {
        UsageGatewayService usageGatewayService = mock(UsageGatewayService.class);
        Instant createdAt = Instant.now();
        BillingOutboxEntry entry =
                new BillingOutboxEntry(
                        2L,
                        "PAYMENT",
                        "TX-2",
                        "PAYMENT_COLLECTION_SUBMITTED",
                        Map.of(
                                "merchantId", 99L,
                                "transactionReference", "TX-2",
                                "currency", "KES"),
                        OutboxEntryStatus.PROCESSING,
                        0,
                        createdAt,
                        null,
                        createdAt,
                        createdAt);

        new UsageEventOutboxHandler(usageGatewayService).handle(entry);

        verify(usageGatewayService)
                .recordUsage(
                        eq(99L), any(), any(), any(), any(), any(), anyMap(), eq("TX-2"), any());
    }
}
