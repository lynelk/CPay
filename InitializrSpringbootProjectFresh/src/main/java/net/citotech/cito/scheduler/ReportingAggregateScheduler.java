package net.citotech.cito.scheduler;

import java.time.LocalDate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly job backing the F4/N10/O3 reporting aggregates: rolls up yesterday's transaction stats,
 * failure-reason stats, and float balance snapshot into {@code daily_transaction_stats},
 * {@code daily_failure_reason_stats}, and {@code float_balance_snapshots} via
 * {@link ReportingAggregateService}.
 *
 * <p>Purely additive/non-destructive (only inserts/updates new aggregate tables), so - like
 * {@link OperationalDataCleanupScheduler} - it is enabled by default and gated by a single
 * feature flag.
 */
@Component
public class ReportingAggregateScheduler {
    private static final Logger logger = Logger.getLogger(ReportingAggregateScheduler.class.getName());

    private final ReportingAggregateService aggregateService;

    @Value("${cpay.reporting.aggregate.enabled:true}")
    private boolean enabled;

    public ReportingAggregateScheduler(ReportingAggregateService aggregateService) {
        this.aggregateService = aggregateService;
    }

    @Scheduled(cron = "${cpay.reporting.aggregate.cron:0 20 1 * * *}")
    public void runNightlyAggregation() {
        if (!enabled) {
            return;
        }
        LocalDate targetDate = LocalDate.now().minusDays(1);
        try {
            aggregateService.aggregateForDate(targetDate);
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Reporting aggregate job failed for " + targetDate + ": " + ex.getMessage(), ex);
        }
    }
}
