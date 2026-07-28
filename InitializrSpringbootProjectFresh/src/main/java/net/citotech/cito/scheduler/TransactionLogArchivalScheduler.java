package net.citotech.cito.scheduler;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled sweep for TransactionLogArchivalService (audit F3). Off by default: a payments ledger
 * table should not start silently rewriting/deleting rows the moment this ships - an operator must
 * explicitly opt in via cpay.archival.transactions-log.enabled. Purge (physical delete) has its own
 * separate opt-in flag on top of that, so enabling archival alone never causes deletions.
 */
@Component
public class TransactionLogArchivalScheduler {
    private static final Logger logger = Logger.getLogger(TransactionLogArchivalScheduler.class.getName());
    private static final int MAX_BATCHES_PER_RUN = 20;

    private final TransactionLogArchivalService archivalService;

    @Value("${cpay.archival.transactions-log.enabled:false}")
    private boolean enabled;

    @Value("${cpay.archival.transactions-log.retention-days:365}")
    private int retentionDays;

    @Value("${cpay.archival.transactions-log.batch-size:500}")
    private int batchSize;

    @Value("${cpay.archival.transactions-log.purge-enabled:false}")
    private boolean purgeEnabled;

    @Value("${cpay.archival.transactions-log.purge-after-days:730}")
    private int purgeAfterDays;

    public TransactionLogArchivalScheduler(TransactionLogArchivalService archivalService) {
        this.archivalService = archivalService;
    }

    @Scheduled(cron = "${cpay.archival.transactions-log.cron:0 30 2 * * *}")
    public void run() {
        if (!enabled) {
            return;
        }
        try {
            int archived = drain(() -> archivalService.archiveBatch(retentionDays, batchSize));
            int purged = purgeEnabled ? drain(() -> archivalService.purgeBatch(purgeAfterDays, batchSize)) : 0;
            if (archived > 0 || purged > 0) {
                logger.log(Level.INFO, "Transaction log archival: archived={0}, purged={1}",
                        new Object[]{archived, purged});
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Transaction log archival run failed: " + ex.getMessage(), ex);
        }
    }

    private int drain(java.util.function.IntSupplier batchRunner) {
        int total = 0;
        for (int i = 0; i < MAX_BATCHES_PER_RUN; i++) {
            int count = batchRunner.getAsInt();
            total += count;
            if (count == 0) {
                break;
            }
        }
        return total;
    }
}
