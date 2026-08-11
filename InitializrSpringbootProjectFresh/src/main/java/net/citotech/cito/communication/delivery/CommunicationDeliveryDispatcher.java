package net.citotech.cito.communication.delivery;

import java.util.logging.Level;
import java.util.logging.Logger;
import net.citotech.cito.communication.email.EmailDeliveryService;
import net.citotech.cito.communication.email.EmailSendRequest;
import net.citotech.cito.communication.email.EmailSendResult;
import net.citotech.cito.communication.sms.SmsGatewayAdapter;
import net.citotech.cito.communication.sms.SmsSendRequest;
import net.citotech.cito.communication.sms.SmsSendResult;
import org.springframework.stereotype.Service;

/**
 * Provider-neutral outbound dispatcher (ISO domain mapping: communication/delivery, track B5a). One
 * entry point that every channel producer (campaign sweeps, WhatsApp sends, USSD responses) calls
 * to deliver one message: it writes a {@code communication_message_deliveries} row (V53) through
 * {@link DeliveryLogRepository}, hands the send to the channel's adapter — SMS via the {@code
 * ProviderRouter} rule-based {@link SmsGatewayAdapter}, EMAIL via {@link EmailDeliveryService} —
 * and records the terminal status with trace/response.
 *
 * <p>WHATSAPP and USSD fail closed for now: no B4 adapters exist yet, so a send is recorded
 * REJECTED (refundable, audit P5) with an explicit "adapter not yet implemented" trace. The
 * delivery row therefore never reaches SENT and the usage relay (B5b) never meters it — an honest
 * extension point that must not silently report a WhatsApp message as delivered.
 */
@Service
public class CommunicationDeliveryDispatcher {

    private static final Logger logger =
            Logger.getLogger(CommunicationDeliveryDispatcher.class.getName());

    private final DeliveryLogRepository deliveryLogRepository;
    private final SmsGatewayAdapter smsGateway;
    private final EmailDeliveryService emailDeliveryService;

    public CommunicationDeliveryDispatcher(
            DeliveryLogRepository deliveryLogRepository,
            SmsGatewayAdapter smsGateway,
            EmailDeliveryService emailDeliveryService) {
        this.deliveryLogRepository = deliveryLogRepository;
        this.smsGateway = smsGateway;
        this.emailDeliveryService = emailDeliveryService;
    }

    /**
     * Delivers one message and records the outcome in the V53 delivery log. Returns the delivery
     * row id plus the terminal status so callers (campaign sweeps) can correlate item state.
     */
    public DeliveryOutcome dispatch(
            long merchantId,
            String channel,
            String recipient,
            String subject,
            String content,
            String providerCode,
            Long referenceId) {
        String normalizedChannel = normalizeChannel(channel);
        long deliveryId =
                deliveryLogRepository.insert(
                        merchantId, normalizedChannel, providerCode, null, referenceId, recipient);
        try {
            DeliveryStatus status;
            String trace;
            String gwResponse;
            switch (normalizedChannel) {
                case "EMAIL" -> {
                    EmailSendResult result =
                            emailDeliveryService.send(
                                    new EmailSendRequest(recipient, subject, content));
                    status =
                            result.status() == EmailSendResult.Status.SENT
                                    ? DeliveryStatus.SENT
                                    : DeliveryStatus.FAILED;
                    trace = result.trace();
                    gwResponse = result.response();
                }
                case "WHATSAPP", "USSD" -> {
                    status = DeliveryStatus.REJECTED;
                    trace = normalizedChannel + " adapter not yet implemented";
                    gwResponse = "";
                }
                default -> {
                    SmsSendResult result =
                            smsGateway.send(
                                    new SmsSendRequest(
                                            deliveryId,
                                            merchantId,
                                            content,
                                            recipient,
                                            providerCode));
                    status = mapSmsStatus(result);
                    trace = result.trace();
                    gwResponse = result.gwResponse();
                }
            }
            deliveryLogRepository.updateStatus(deliveryId, status, trace, gwResponse);
            return new DeliveryOutcome(deliveryId, status);
        } catch (Exception ex) {
            logger.log(
                    Level.WARNING,
                    "Delivery failed for channel "
                            + normalizedChannel
                            + " recipient "
                            + recipient
                            + ": "
                            + ex.getMessage(),
                    ex);
            deliveryLogRepository.updateStatus(
                    deliveryId, DeliveryStatus.FAILED, ex.getMessage(), "");
            return new DeliveryOutcome(deliveryId, DeliveryStatus.FAILED);
        }
    }

    private DeliveryStatus mapSmsStatus(SmsSendResult result) {
        return switch (result.status()) {
            case SENT -> DeliveryStatus.SENT;
            case REJECTED -> DeliveryStatus.REJECTED;
            default -> DeliveryStatus.FAILED;
        };
    }

    private String normalizeChannel(String channel) {
        return channel == null || channel.isBlank() ? "SMS" : channel.trim().toUpperCase();
    }

    public record DeliveryOutcome(long deliveryId, DeliveryStatus status) {}
}
