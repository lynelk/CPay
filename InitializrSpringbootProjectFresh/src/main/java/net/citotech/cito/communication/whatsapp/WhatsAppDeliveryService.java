package net.citotech.cito.communication.whatsapp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.citotech.cito.communication.routing.CommunicationRoutingRepository;
import net.citotech.cito.communication.routing.CommunicationRoutingRepository.ProviderRow;
import net.citotech.cito.communication.routing.CommunicationRoutingRepository.RuleRow;
import org.springframework.stereotype.Service;

/** Routes WhatsApp sends through the same merchant/platform precedence model used by SMS. */
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
        for (RuleRow rule : routingRepository.effectiveCandidates(CHANNEL, request.merchantId())) {
            if (!"YES".equalsIgnoreCase(rule.enabledFlag())) {
                continue;
            }
            ProviderRow provider = routingRepository.provider(rule.providerCode(), CHANNEL).orElse(null);
            if (provider == null || !"YES".equalsIgnoreCase(provider.enabledFlag())) {
                continue;
            }
            WhatsAppGatewayAdapter adapter = adapters.get(provider.providerCode());
            if (adapter != null) {
                return adapter.send(request);
            }
        }
        throw new IllegalStateException("No enabled WhatsApp routing rule and adapter are configured");
    }
}
