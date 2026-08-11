package net.citotech.cito.communication.campaign;

import java.util.logging.Level;
import java.util.logging.Logger;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ShedLock-guarded sweep that dispatches QUEUED/RUNNING campaign items through {@link
 * CampaignService#sweepDue} (V52, track B4). Opt-in via {@code cpay.communication.campaign.enabled}
 * (default off) so the legacy SMS pend-send path remains the operator-triggered default; enabling
 * it is safe in HA because the database-backed lock serializes instances, mirroring {@code
 * SmsDeliveryScheduler}.
 */
@Component
@ConditionalOnProperty(value = "cpay.communication.campaign.enabled", havingValue = "true")
public class CampaignScheduler {

    private static final Logger logger = Logger.getLogger(CampaignScheduler.class.getName());

    private final CampaignService campaignService;

    public CampaignScheduler(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @Scheduled(fixedDelayString = "${cpay.communication.campaign.fixed-delay-ms:60000}")
    @SchedulerLock(
            name = "communicationCampaignSweep",
            lockAtMostFor = "PT15M",
            lockAtLeastFor = "PT30S")
    public void sweepDue() {
        try {
            int processed = campaignService.sweepDue(200);
            if (processed > 0) {
                logger.log(Level.INFO, "Campaign sweep dispatched {0} item(s)", processed);
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Campaign sweep failed: " + ex.getMessage(), ex);
        }
    }
}
