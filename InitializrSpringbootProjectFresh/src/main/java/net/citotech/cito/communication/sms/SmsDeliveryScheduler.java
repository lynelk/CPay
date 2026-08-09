package net.citotech.cito.communication.sms;

import java.util.logging.Level;
import java.util.logging.Logger;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Opt-in scheduled driver for SMS pending-send delivery (ISO domain mapping: communication/sms).
 *
 * <p>The legacy pend-send batch ran only when an operator/script hit {@code POST
 * /transactions/testSendPendingSmsCron}; this scheduler adds the same {@code
 * SmsDeliveryService.deliverDue} loop as a cluster-safe, ShedLock-guarded cron that is off by
 * default so existing single-instance deployments keep their current behavior until they opt in.
 * With the B0 @SchedulerLock already on the HTTP endpoint, enabling both paths is safe in HA: the
 * database-backed lock serializes them.
 */
@Component
@ConditionalOnProperty(value = "cpay.sms.delivery.enabled", havingValue = "true")
public class SmsDeliveryScheduler {

    private static final Logger logger = Logger.getLogger(SmsDeliveryScheduler.class.getName());

    private final SmsDeliveryService smsDeliveryService;

    public SmsDeliveryScheduler(SmsDeliveryService smsDeliveryService) {
        this.smsDeliveryService = smsDeliveryService;
    }

    @Scheduled(fixedDelayString = "${cpay.sms.delivery.fixed-delay-ms:60000}")
    @SchedulerLock(name = "smsPendSendDelivery", lockAtMostFor = "PT15M", lockAtLeastFor = "PT30S")
    public void deliverDue() {
        try {
            int processed = smsDeliveryService.deliverDue(1000);
            if (processed > 0) {
                logger.log(
                        Level.INFO, "SMS pending-send sweep processed {0} message(s)", processed);
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "SMS delivery sweep failed: " + ex.getMessage(), ex);
        }
    }
}
