package net.citotech.cito.communication.whatsapp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.citotech.cito.communication.routing.CommunicationRoutingRepository;
import net.citotech.cito.communication.routing.CommunicationRoutingRepository.ProviderRow;
import net.citotech.cito.communication.routing.CommunicationRoutingRepository.RuleRow;
import org.springframework.stereotype.Service;

/** Routes WhatsApp sends through the same merchant/platform rule model used by SMS. */
@Service
public class WhatsAppDeliveryService {
    public static final String CHANNEL = "WHATSAPP";

    private final CommunicationRoutingRepository routingRepository;
    private final Map<String, WhatsAppGatewayAdapter> adapters;

    public WhatsAppDeliveryService(
            CommunicationRoutingRepository routingRepository,
            List<WhatsAppGatewayAdapter> adapters) {
        this.routingRepository = routingRepository;
        Map<String, WhatsAppGatewayAdapter> byCode = new HashMap<>();
        for (WhatsAppGatewayAdapter adapter : adapters) {
            byCode.put(adapter.providerCode(), adapter);
        }
        this.adapters = Map.copyOf(byCode);
    }

    public WhatsAppSendResult send(WhatsAppSendRequest request) {
        RuleRow rule =
                routingRepository
                        .effectiveRule(CHANNEL, request.merchantId())
                        .filter(row -> "YES".equalsIgnoreCase(row.enabledFlag()))
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "No enabled WhatsApp routing rule is configured"));
        ProviderRow provider =
                routingRepository
                        .provider(rule.providerCode(), CHANNEL)
                        .filter(row -> "YES".equalsIgnoreCase(row.enabledFlag()))
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "WhatsApp provider is missing or disabled: "
                                                        + rule.providerCode()));
        WhatsAppGatewayAdapter adapter = adapters.get(provider.providerCode());
        if (adapter == null) {
            throw new IllegalStateException(
                    "No WhatsApp adapter registered for provider " + provider.providerCode());
        }
        return adapter.send(request);
    }
}
