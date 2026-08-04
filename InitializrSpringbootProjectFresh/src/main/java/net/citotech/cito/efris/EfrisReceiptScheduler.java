package net.citotech.cito.efris;

import java.util.logging.Level;
import java.util.logging.Logger;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sweeps the EFRIS e-receipt outbox on a fixed delay, delivering due pending receipts through
 * {@link EfrisReceiptService#deliverDue}. Follows the webhook-delivery and reporting-aggregate
 * scheduler conventions: fixed-delay trigger, ShedLock single-runner lock, and an enabled flag so
 * operators can turn it off without a redeploy. Failures are caught and logged at WARNING so one
 * bad receipt never stops the remaining outbox.
 */
@Component
public class EfrisReceiptScheduler {
    private static final Logger logger = Logger.getLogger(EfrisReceiptScheduler.class.getName());

    private final EfrisReceiptService receiptService;

    @Value("${cpay.efris.deliver.enabled:true}")
    private boolean enabled;

    public EfrisReceiptScheduler(EfrisReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @Scheduled(fixedDelayString = "${cpay.efris.deliver.fixed-delay-ms:60000}")
    @SchedulerLock(name = "efrisReceiptSweep", lockAtMostFor = "PT10M", lockAtLeastFor = "PT5S")
    public void sweepDueReceipts() {
        if (!enabled) {
            return;
        }
        try {
            int backfilled = receiptService.enqueueMissingSuccessfulPayins(7, 500);
            if (backfilled > 0) {
                logger.log(Level.INFO, "EFRIS e-receipt sweep queued {0} receipt(s)", backfilled);
            }
            int processed = receiptService.deliverDue(100);
            if (processed > 0) {
                logger.log(Level.INFO, "EFRIS e-receipt sweep delivered {0} receipt(s)", processed);
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "EFRIS receipt sweep failed: " + ex.getMessage(), ex);
        }
    }
}
