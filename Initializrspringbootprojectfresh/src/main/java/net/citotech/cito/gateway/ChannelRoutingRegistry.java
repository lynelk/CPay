package net.citotech.cito.gateway;

import org.springframework.stereotype.Component;

/**
 * Static bridge so the legacy {@code *PaymentGateway} classes (plain static utility classes, not
 * Spring beans) can consult the DB-backed {@link ChannelRoutingService} (audit B4). Each
 * gateway's {@code isValidMisdn} keeps its hardcoded array as a fallback for environments where
 * the routing table hasn't been populated (or during startup before the first bean is wired).
 */
@Component
public class ChannelRoutingRegistry {
    private static volatile ChannelRoutingService routingService;

    public ChannelRoutingRegistry(ChannelRoutingService routingService) {
        ChannelRoutingRegistry.routingService = routingService;
    }

    /** Returns null (defer to the hardcoded fallback) if the DB has no configured prefixes for this gateway. */
    public static Boolean matchesConfiguredPrefix(String gatewayId, String msisdn) {
        ChannelRoutingService service = routingService;
        if (service == null || service.prefixesFor(gatewayId).isEmpty()) {
            return null;
        }
        return service.matches(gatewayId, msisdn);
    }
}
