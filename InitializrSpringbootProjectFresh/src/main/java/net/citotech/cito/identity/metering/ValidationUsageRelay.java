package net.citotech.cito.identity.metering;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.citotech.cito.billing.usage.UsageGatewayService;
import net.citotech.cito.identity.metering.ValidationUsageRepository.ValidationUsage;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Validation usage relay (Track B Phase 6): converts {@code billable_attempt='Y'} {@code
 * validation_usage} rows into {@code billing_usage_events} (V40) through the existing idempotent
 * {@link UsageGatewayService}, then marks the usage row relayed. This is the Validation → Billing
 * meter bridge of the guide's Phase 6: the billing engine needs no new rating code, only a source
 * of usage events.
 *
 * <p>Watermark model mirrors {@code CommunicationUsageRelay}: a ShedLock-guarded sweep advances a
 * cursor ({@code last_id}) in bounded batches. {@code UsageGatewayService#recordUsage} dedupes by
 * idempotency key ({@code validation:<capability>:<usageId>}), so concurrent/restarted sweeps are
 * no-ops, not duplicates. A row that fails to relay (e.g. tenant resolver misconfigured) stays
 * {@code relayed_flag='N'} and is retried on the next sweep — never skipped. Only
 * {@code billable_attempt='Y'} rows are relayed; {@code N} rows (internal technical retries the
 * provider contract does not charge for) are never metered here.
 */
@Component
@ConditionalOnProperty(
        value = "cpay.validation.usage.relay.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ValidationUsageRelay {

    private static final Logger logger = Logger.getLogger(ValidationUsageRelay.class.getName());

    private static final int DEFAULT_BATCH_LIMIT = 100;
    private static final String IDEMPOTENCY_PREFIX = "validation:";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ValidationUsageRepository usageRepository;
    private final UsageGatewayService usageGatewayService;

    public ValidationUsageRelay(
            NamedParameterJdbcTemplate jdbcTemplate,
            ValidationUsageRepository usageRepository,
            UsageGatewayService usageGatewayService) {
        this.jdbcTemplate = jdbcTemplate;
        this.usageRepository = usageRepository;
        this.usageGatewayService = usageGatewayService;
    }

    @Scheduled(fixedDelayString = "${cpay.validation.usage.relay.fixed-delay-ms:30000}")
    @SchedulerLock(
            name = "validationUsageRelay",
            lockAtMostFor = "PT5M",
            lockAtLeastFor = "PT10S")
    public void relayDue() {
        try {
            int relayed = relayDue(DEFAULT_BATCH_LIMIT);
            if (relayed > 0) {
                logger.log(
                        Level.INFO, "Validation usage relay processed {0} row(s)", relayed);
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Validation usage relay failed: " + ex.getMessage(), ex);
        }
    }

    /** Sweeps the un-relayed usage rows once. Package visible so tests can drive a sweep. */
    int relayDue(int limit) {
        int relayed = 0;
        long cursor = relayCursor();
        while (true) {
            long before = cursor;
            List<ValidationUsage> batch = usageRepository.since(cursor, limit);
            if (batch.isEmpty()) {
                break;
            }
            for (ValidationUsage usage : batch) {
                if (relayOne(usage)) {
                    relayed++;
                    cursor = usage.id();
                }
            }
            // No progress across a full batch (every row failed) — stop rather than spinning;
            // the next sweep retries them.
            if (cursor == before) {
                break;
            }
        }
        saveCursor(cursor);
        return relayed;
    }

    private boolean relayOne(ValidationUsage usage) {
        try {
            Map<String, String> dimensions = new HashMap<>();
            dimensions.put("capability", usage.capability());
            dimensions.put("provider_code", usage.providerCode());
            if (usage.providerOperation() != null && !usage.providerOperation().isBlank()) {
                dimensions.put("provider_operation", usage.providerOperation());
            }
            usageGatewayService.recordUsage(
                    usage.merchantId(),
                    serviceCodeFor(usage.capability()),
                    meterCodeFor(usage.capability()),
                    Instant.now(),
                    usage.providerCost() == null ? BigDecimal.ONE : usage.providerCost(),
                    usage.providerCurrency() == null ? "UGX" : usage.providerCurrency(),
                    dimensions,
                    "VALIDATION_USAGE:" + usage.id(),
                    IDEMPOTENCY_PREFIX + usage.capability() + ":" + usage.id());
            markRelayed(usage.id());
            return true;
        } catch (Exception ex) {
            logger.log(
                    Level.WARNING,
                    "Validation usage relay failed for usage "
                            + usage.id()
                            + ": "
                            + ex.getMessage(),
                    ex);
            return false;
        }
    }

    private long relayCursor() {
        List<Long> rows =
                jdbcTemplate.query(
                        "SELECT last_usage_id FROM validation_usage_watermark WHERE id=1",
                        new MapSqlParameterSource(),
                        (rs, rowNum) -> rs.getLong("last_usage_id"));
        if (rows.isEmpty()) {
            jdbcTemplate.update(
                    "INSERT INTO validation_usage_watermark (id, last_usage_id) VALUES (1, 0)"
                            + " ON DUPLICATE KEY UPDATE id=id",
                    new MapSqlParameterSource());
            return 0L;
        }
        return rows.get(0);
    }

    private void saveCursor(long lastUsageId) {
        jdbcTemplate.update(
                "INSERT INTO validation_usage_watermark (id, last_usage_id) VALUES (1, :last_usage_id)"
                        + " ON DUPLICATE KEY UPDATE last_usage_id=:last_usage_id",
                new MapSqlParameterSource("last_usage_id", lastUsageId));
    }

    private void markRelayed(long usageId) {
        jdbcTemplate.update(
                "UPDATE validation_usage SET relayed_flag='Y' WHERE id=:id AND relayed_flag='N'",
                new MapSqlParameterSource("id", usageId));
    }

    private String serviceCodeFor(String capability) {
        return "VALIDATION";
    }

    private String meterCodeFor(String capability) {
        return switch (capability) {
            case "NIN", "PERSONAL_INFORMATION", "PHONE_OWNERSHIP" -> "identity_check_count";
            case "KYC_REPORT" -> "kyc_report_count";
            case "CREDIT_ENQUIRY", "CREDIT_REPORT" -> "credit_enquiry_count";
            case "CREDIT_SCORE_CRB", "CREDIT_SCORE_MNO", "CREDIT_SCORE_SACCO",
                    "CREDIT_SCORE_COMBINED" -> "credit_score_count";
            default -> "validation_check_count";
        };
    }
}
