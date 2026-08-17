package net.citotech.cito.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * P0 section 6: production observability baseline.
 *
 * <p>{@code GatewayMetrics} provides the per-transaction/callback/error counters, but the P0 metric
 * list additionally requires operator-facing gauges for the things that go wrong between the
 * counters: payouts stuck awaiting maker-checker, open reconciliation exceptions, open compliance
 * cases, parked webhook deliveries, and merchant API signing failures.
 *
 * <p>Every gauge is registered once with a {@code valueSupplier} that runs a fresh SQL count on
 * every scrape, so no scheduler (and no ShedLock) is needed and the value is always current when
 * Prometheus scrapes. A DB failure during a scrape resolves the gauge to 0 instead of breaking the
 * register call.
 */
@Component
public class ObservabilityScorecardService {

    private static final List<String> SIGNATURE_FAILURE_REASONS = List.of("115", "116", "122");

    private final MeterRegistry meterRegistry;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ObservabilityScorecardService(
            MeterRegistry meterRegistry, NamedParameterJdbcTemplate jdbcTemplate) {
        this.meterRegistry = meterRegistry;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void registerGauges() {
        Gauge.builder(
                        "cpay.payout.approval.pending",
                        this,
                        ObservabilityScorecardService::countPendingPayoutApprovals)
                .description("Payouts awaiting maker-checker approval")
                .register(meterRegistry);
        Gauge.builder(
                        "cpay.reconciliation.exceptions.open",
                        this,
                        ObservabilityScorecardService::countOpenReconciliationExceptions)
                .description("Open reconciliation exceptions (all severities)")
                .register(meterRegistry);
        Gauge.builder(
                        "cpay.reconciliation.exceptions.open_high",
                        this,
                        ObservabilityScorecardService::countOpenHighCriticalExceptions)
                .description("Open HIGH/CRITICAL reconciliation exceptions")
                .register(meterRegistry);
        Gauge.builder(
                        "cpay.compliance.cases.open",
                        this,
                        ObservabilityScorecardService::countOpenComplianceCases)
                .description("Open compliance cases")
                .register(meterRegistry);
        Gauge.builder(
                        "cpay.webhook.deliveries.parked",
                        this,
                        ObservabilityScorecardService::countParkedWebhookDeliveries)
                .description("Merchant webhook deliveries parked after exhausting attempts")
                .register(meterRegistry);
        for (String reason : SIGNATURE_FAILURE_REASONS) {
            Gauge.builder(
                            "cpay.api.signature_failures",
                            this,
                            service -> service.countSignatureFailures(reason))
                    .tag("reason", reason)
                    .description("Merchant API signature verification failures by reason")
                    .register(meterRegistry);
        }
    }

    private double countPendingPayoutApprovals() {
        return count(
                "SELECT COUNT(*) FROM payout_approval_queue WHERE queue_status='PENDING_APPROVAL'");
    }

    private double countOpenReconciliationExceptions() {
        return count("SELECT COUNT(*) FROM reconciliation_exceptions WHERE status='OPEN'");
    }

    private double countOpenHighCriticalExceptions() {
        return count(
                "SELECT COUNT(*) FROM reconciliation_exceptions WHERE status='OPEN' "
                        + "AND severity IN ('HIGH','CRITICAL')");
    }

    private double countOpenComplianceCases() {
        return count("SELECT COUNT(*) FROM compliance_cases WHERE status='OPEN'");
    }

    private double countParkedWebhookDeliveries() {
        return count(
                "SELECT COUNT(*) FROM merchant_webhook_deliveries WHERE delivery_status='PARKED'");
    }

    private double countSignatureFailures(String reason) {
        Map<String, Long> snapshot = SignatureVerificationFailureRegistry.snapshot();
        return snapshot.getOrDefault(reason, 0L);
    }

    private double count(String sql) {
        try {
            Integer value =
                    jdbcTemplate.queryForObject(sql, new MapSqlParameterSource(), Integer.class);
            return value == null ? 0 : value;
        } catch (Exception e) {
            // A metrics read must never take the process down; scrape resolves to 0 this cycle.
            return 0;
        }
    }
}
