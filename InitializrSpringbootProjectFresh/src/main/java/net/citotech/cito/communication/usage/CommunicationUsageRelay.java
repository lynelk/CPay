package net.citotech.cito.communication.usage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.citotech.cito.billing.usage.UsageGatewayService;
import net.citotech.cito.communication.delivery.DeliveryLogRepository;
import net.citotech.cito.communication.delivery.MessageDelivery;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Per-channel usage relay (track B5b): converts {@code SENT} {@code
 * communication_message_deliveries} (V53) rows into {@code billing_usage_events} (V40) via the
 * existing idempotent {@link UsageGatewayService} path, then marks the delivery row billed. It is
 * the Communication → Billing meter bridge of the ISO/domain plan: the billing engine needs no new
 * pricing or rating code, only a source of usage events, and this relay is that source for
 * SMS/EMAIL/WHATSAPP/USSD.
 *
 * <p>Watermark model: one {@code communication_usage_watermark} row per channel tracks the last
 * delivery id relayed. A ShedLock-guarded sweep moves each channel's watermark forward in bounded
 * batches (default 100 rows). The relay never re-emits a billed row — {@code
 * DeliveryLogRepository.sentSince} excludes {@code billed_flag='N'} — and {@link
 * UsageGatewayService#recordUsage} dedupes by {@code idempotencyKey} ({@code
 * comm:<channel>:<deliveryId>}), so concurrent/restarted sweeps are no-ops, not duplicates. A row
 * that fails to relay (e.g. the tenant resolver or meters are misconfigured) is left unbilled and
 * retried on the next sweep — never skipped, so an outage never silently drops a metered event.
 */
@Component
@ConditionalOnProperty(
        value = "cpay.communication.usage.relay.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class CommunicationUsageRelay {

    private static final Logger logger = Logger.getLogger(CommunicationUsageRelay.class.getName());

    private static final int DEFAULT_BATCH_LIMIT = 100;
    private static final String IDEMPOTENCY_PREFIX = "comm:";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final DeliveryLogRepository deliveryLogRepository;
    private final UsageGatewayService usageGatewayService;

    public CommunicationUsageRelay(
            NamedParameterJdbcTemplate jdbcTemplate,
            DeliveryLogRepository deliveryLogRepository,
            UsageGatewayService usageGatewayService) {
        this.jdbcTemplate = jdbcTemplate;
        this.deliveryLogRepository = deliveryLogRepository;
        this.usageGatewayService = usageGatewayService;
    }

    @Scheduled(fixedDelayString = "${cpay.communication.usage.relay.fixed-delay-ms:30000}")
    @SchedulerLock(
            name = "communicationUsageRelay",
            lockAtMostFor = "PT5M",
            lockAtLeastFor = "PT10S")
    public void relayDue() {
        try {
            int relayed = relayDue(DEFAULT_BATCH_LIMIT);
            if (relayed > 0) {
                logger.log(
                        Level.INFO, "Communication usage relay processed {0} delivery(s)", relayed);
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Communication usage relay failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Sweeps every registered channel once. Returns the total number of deliveries relayed. Package
     * visible so tests can drive a sweep without the {@code @Scheduled} machinery.
     */
    int relayDue(int limit) {
        int total = 0;
        for (String channel : registeredChannels()) {
            total += relayChannel(channel, limit);
        }
        return total;
    }

    private int relayChannel(String channel, int limit) {
        int relayed = 0;
        long watermark = watermarkFor(channel);
        while (true) {
            long before = watermark;
            List<MessageDelivery> batch = deliveryLogRepository.sentSince(channel, before, limit);
            if (batch.isEmpty()) {
                break;
            }
            for (MessageDelivery delivery : batch) {
                if (relayOne(delivery)) {
                    relayed++;
                    watermark = delivery.id();
                }
            }
            // No progress across a full batch (every row failed to relay, e.g. the tenant
            // resolver or meters are down) - stop instead of spinning on the same rows; the
            // next sweep retries them.
            if (watermark == before) {
                break;
            }
        }
        saveWatermark(channel, watermark);
        return relayed;
    }

    private boolean relayOne(MessageDelivery delivery) {
        try {
            Map<String, String> dimensions = new HashMap<>();
            dimensions.put("channel", delivery.channel());
            if (delivery.providerCode() != null && !delivery.providerCode().isBlank()) {
                dimensions.put("provider_code", delivery.providerCode());
            }
            usageGatewayService.recordUsage(
                    delivery.merchantId(),
                    serviceCodeFor(delivery.channel()),
                    meterCodeFor(delivery.channel()),
                    Instant.now(),
                    BigDecimal.ONE,
                    null,
                    dimensions,
                    "COMM_DELIVERY:" + delivery.id(),
                    IDEMPOTENCY_PREFIX + delivery.channel() + ":" + delivery.id());
            deliveryLogRepository.markBilled(delivery.id());
            return true;
        } catch (Exception ex) {
            logger.log(
                    Level.WARNING,
                    "Usage relay failed for delivery " + delivery.id() + ": " + ex.getMessage(),
                    ex);
            return false;
        }
    }

    private List<String> registeredChannels() {
        return jdbcTemplate.query(
                "SELECT channel FROM communication_usage_watermark ORDER BY channel ASC",
                new MapSqlParameterSource(),
                (rs, rowNum) -> rs.getString("channel"));
    }

    private long watermarkFor(String channel) {
        List<Long> rows =
                jdbcTemplate.query(
                        "SELECT last_delivery_id FROM communication_usage_watermark"
                                + " WHERE channel=:channel",
                        new MapSqlParameterSource("channel", channel),
                        (rs, rowNum) -> rs.getLong("last_delivery_id"));
        return rows.isEmpty() ? 0L : rows.get(0);
    }

    private void saveWatermark(String channel, long lastDeliveryId) {
        jdbcTemplate.update(
                "UPDATE communication_usage_watermark SET last_delivery_id=:last_delivery_id,"
                        + " processed_flag='Y' WHERE channel=:channel",
                new MapSqlParameterSource()
                        .addValue("channel", channel)
                        .addValue("last_delivery_id", lastDeliveryId));
    }

    private String serviceCodeFor(String channel) {
        return switch (channel) {
            case "EMAIL" -> "EMAIL";
            case "WHATSAPP" -> "WHATSAPP";
            case "USSD" -> "USSD";
            default -> "SMS";
        };
    }

    private String meterCodeFor(String channel) {
        return switch (channel) {
            case "EMAIL" -> "email_delivered_count";
            case "WHATSAPP" -> "whatsapp_message_count";
            case "USSD" -> "ussd_session_count";
            default -> "sms_sent_count";
        };
    }
}
