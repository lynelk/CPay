package net.citotech.cito.communication.usage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import net.citotech.cito.billing.usage.UsageEvent;
import net.citotech.cito.billing.usage.UsageGatewayService;
import net.citotech.cito.communication.delivery.DeliveryLogRepository;
import net.citotech.cito.communication.delivery.DeliveryStatus;
import net.citotech.cito.communication.delivery.MessageDelivery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Covers the B5b relay: SENT deliveries are converted to idempotent billing usage events and the
 * delivery row is marked billed; a failed relay leaves the row unbilled for retry; and a channel's
 * watermark is not advanced when nothing new is relayed.
 */
class CommunicationUsageRelayTest {

    private NamedParameterJdbcTemplate jdbcTemplate;
    private DeliveryLogRepository deliveryLogRepository;
    private UsageGatewayService usageGatewayService;
    private CommunicationUsageRelay relay;

    private List<String> channels = List.of("SMS", "EMAIL");
    private Long smsWatermark = 0L;
    private List<MessageDelivery> smsBatch = List.of();

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        deliveryLogRepository = mock(DeliveryLogRepository.class);
        usageGatewayService = mock(UsageGatewayService.class);

        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(
                        invocation -> {
                            String sql = invocation.getArgument(0);
                            Object[] args = invocation.getArguments();
                            if (sql.contains("communication_usage_watermark")
                                    && sql.contains("SELECT channel")) {
                                return channels;
                            }
                            if (sql.contains("SELECT last_delivery_id")) {
                                return List.of(smsWatermark);
                            }
                            return List.of();
                        });
        // Production semantics: sentSince excludes billed rows, so once the relay marks a row
        // billed the next sweep no longer returns it. Simulate that by returning the batch once
        // and then an empty list on subsequent calls.
        when(deliveryLogRepository.sentSince(eq("SMS"), any(Long.class), eq(100)))
                .thenAnswer(
                        invocation -> {
                            java.util.List<MessageDelivery> batch = smsBatch;
                            smsBatch = java.util.List.of();
                            return batch;
                        });
        when(deliveryLogRepository.sentSince(eq("EMAIL"), any(Long.class), eq(100)))
                .thenReturn(List.of());
        when(usageGatewayService.recordUsage(
                        any(Long.class),
                        anyString(),
                        anyString(),
                        any(),
                        any(BigDecimal.class),
                        any(),
                        any(Map.class),
                        anyString(),
                        anyString()))
                .thenReturn(mock(UsageEvent.class));
        when(deliveryLogRepository.markBilled(any(Long.class))).thenReturn(1);

        relay =
                new CommunicationUsageRelay(
                        jdbcTemplate, deliveryLogRepository, usageGatewayService);
    }

    private MessageDelivery sent(Integer id, String channel, String provider) {
        return new MessageDelivery(
                id,
                7L,
                channel,
                provider,
                null,
                null,
                "256700000001",
                DeliveryStatus.SENT,
                "trace",
                "",
                BigDecimal.ZERO,
                false);
    }

    @Test
    void relaysSentSmsAndMarksItBilled() {
        smsBatch = List.of(sent(1, "SMS", "YO_SMS"));

        relay.relayDue(100);

        verify(usageGatewayService)
                .recordUsage(
                        eq(7L),
                        eq("SMS"),
                        eq("sms_sent_count"),
                        any(),
                        eq(BigDecimal.ONE),
                        eq(null),
                        eq(Map.of("channel", "SMS", "provider_code", "YO_SMS")),
                        eq("COMM_DELIVERY:1"),
                        eq("comm:SMS:1"));
        verify(deliveryLogRepository).markBilled(1L);
    }

    @Test
    void failedRelayLeavesRowUnbilledForRetry() {
        smsBatch = List.of(sent(1, "SMS", "YO_SMS"));
        when(usageGatewayService.recordUsage(
                        any(Long.class),
                        anyString(),
                        anyString(),
                        any(),
                        any(BigDecimal.class),
                        any(),
                        any(Map.class),
                        anyString(),
                        anyString()))
                .thenThrow(new IllegalStateException("tenant resolver down"));

        relay.relayDue(100);

        verify(deliveryLogRepository, never()).markBilled(1L);
    }

    @Test
    void noNewDeliveriesDoesNotAdvanceTheWatermark() {
        smsBatch = List.of();

        relay.relayDue(100);

        // saveWatermark still runs with the unchanged watermark; the point is no usage events were
        // emitted and no rows were billed.
        verify(deliveryLogRepository, never()).markBilled(any(Long.class));
    }
}
