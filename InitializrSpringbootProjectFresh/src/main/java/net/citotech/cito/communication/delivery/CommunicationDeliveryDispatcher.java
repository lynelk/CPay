package net.citotech.cito.communication.delivery;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.citotech.cito.communication.domain.CommunicationChannel;
import net.citotech.cito.communication.email.EmailDeliveryService;
import net.citotech.cito.communication.email.EmailSendRequest;
import net.citotech.cito.communication.email.EmailSendResult;
import net.citotech.cito.communication.provider.CommunicationProviderAdapter;
import net.citotech.cito.communication.provider.ProviderRegistry;
import net.citotech.cito.communication.provider.ProviderSendRequest;
import net.citotech.cito.communication.provider.ProviderSendResult;
import net.citotech.cito.communication.provider.SmsCommunicationProviderAdapter;
import net.citotech.cito.communication.sms.SmsGatewayAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Provider-neutral outbound dispatcher (ISO domain mapping: communication/delivery, track B5a). One
 * entry point that every channel producer (campaign sweeps, WhatsApp sends, USSD responses) calls
 * to deliver one message: it writes a {@code communication_message_deliveries} row (V58) through
 * {@link DeliveryLogRepository}, hands the send to the channel's adapter — EMAIL via {@link
 * EmailDeliveryService}, every other channel via a {@link CommunicationProviderAdapter} resolved
 * from the {@link ProviderRegistry} by provider code + channel — and records the terminal status
 * with trace/response.
 *
 * <p>Channels with no registered adapter (e.g. WHATSAPP/USSD before a provider is certified) fail
 * closed: the send is recorded REJECTED (refundable, audit P5) with an explicit "adapter not yet
 * implemented" trace, so the delivery row never reaches SENT and the usage relay (B5b) never
 * meters it.
 */
@Service
public class CommunicationDeliveryDispatcher {

    private static final Logger logger =
            Logger.getLogger(CommunicationDeliveryDispatcher.class.getName());

    private final DeliveryLogRepository deliveryLogRepository;
    private final ProviderRegistry providerRegistry;
    private final EmailDeliveryService emailDeliveryService;

    /**
     * Legacy constructor kept for compatibility with existing callers/tests: the single injected
     * {@link SmsGatewayAdapter} is registered under every known SMS provider code so a legacy
     * dispatcher instance handles any SMS code exactly as before.
     */
    public CommunicationDeliveryDispatcher(
            DeliveryLogRepository deliveryLogRepository,
            SmsGatewayAdapter smsGateway,
            EmailDeliveryService emailDeliveryService) {
        this(
                deliveryLogRepository,
                new ProviderRegistry(
                        List.of(
                                new SmsCommunicationProviderAdapter(
                                        smsGateway, "LEGACY_SETTINGS"),
                                new SmsCommunicationProviderAdapter(smsGateway, "YO_SMS"),
                                new SmsCommunicationProviderAdapter(
                                        smsGateway, "AFRICAS_TALKING"),
                                new SmsCommunicationProviderAdapter(smsGateway, "TWILIO_SMS"))),
                emailDeliveryService);
    }

    /** Primary production constructor: dispatches every non-email channel through the registry. */
    @Autowired
    public CommunicationDeliveryDispatcher(
            DeliveryLogRepository deliveryLogRepository,
            ProviderRegistry providerRegistry,
            EmailDeliveryService emailDeliveryService) {
        this.deliveryLogRepository = deliveryLogRepository;
        this.providerRegistry = providerRegistry;
        this.emailDeliveryService = emailDeliveryService;
    }

    /**
     * Delivers one message and records the outcome in the V58 delivery log. Returns the delivery
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
            if ("EMAIL".equals(normalizedChannel)) {
                EmailSendResult result =
                        emailDeliveryService.send(
                                new EmailSendRequest(recipient, subject, content));
                status =
                        result.status() == EmailSendResult.Status.SENT
                                ? DeliveryStatus.SENT
                                : DeliveryStatus.FAILED;
                trace = result.trace();
                gwResponse = result.response();
            } else {
                CommunicationChannel channelEnum =
                        CommunicationChannel.fromString(normalizedChannel);
                CommunicationProviderAdapter adapter =
                        providerRegistry.find(providerCode, channelEnum).orElse(null);
                if (adapter == null) {
                    status = DeliveryStatus.REJECTED;
                    trace = normalizedChannel + " adapter not yet implemented";
                    gwResponse = "";
                } else {
                    ProviderSendResult result =
                            adapter.send(
                                    new ProviderSendRequest(
                                            0L,
                                            deliveryId,
                                            merchantId,
                                            recipient,
                                            subject,
                                            content,
                                            null,
                                            Map.of(),
                                            Map.of()));
                    status = mapProviderStatus(result);
                    trace = result.trace();
                    gwResponse = result.safeResponse();
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

    private DeliveryStatus mapProviderStatus(ProviderSendResult result) {
        if (result == null || result.status() == null) {
            return DeliveryStatus.FAILED;
        }
        return switch (result.status()) {
            case ACCEPTED, SENT, DELIVERED -> DeliveryStatus.SENT;
            case REJECTED -> DeliveryStatus.REJECTED;
            case FAILED, UNKNOWN -> DeliveryStatus.FAILED;
        };
    }

    private String normalizeChannel(String channel) {
        return channel == null || channel.isBlank() ? "SMS" : channel.trim().toUpperCase();
    }

    public record DeliveryOutcome(long deliveryId, DeliveryStatus status) {}
}
