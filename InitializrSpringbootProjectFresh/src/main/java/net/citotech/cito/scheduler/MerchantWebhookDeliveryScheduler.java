package net.citotech.cito.scheduler;

import java.util.logging.Level;
import java.util.logging.Logger;
import net.citotech.cito.webhook.MerchantWebhookService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "cpay.webhooks.delivery.enabled", havingValue = "true", matchIfMissing = true)
public class MerchantWebhookDeliveryScheduler {
    private static final Logger logger = Logger.getLogger(MerchantWebhookDeliveryScheduler.class.getName());

    private final MerchantWebhookService webhookService;

    public MerchantWebhookDeliveryScheduler(MerchantWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @Scheduled(fixedDelayString = "${cpay.webhooks.delivery.fixed-delay-ms:60000}")
    public void deliverDue() {
        try {
            webhookService.deliverDue(50);
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Merchant webhook delivery failed: " + ex.getMessage(), ex);
        }
    }
}
