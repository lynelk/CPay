package net.citotech.cito.communication.provider;

import java.util.Map;

/**
 * Channel-neutral send request handed to a {@link CommunicationProviderAdapter} (ISO domain
 * mapping: communication/provider). Provider adapters translate this into their own DTO; they must
 * never receive or expose provider credentials through this record.
 */
public record ProviderSendRequest(
        long communicationId,
        long deliveryId,
        long merchantId,
        String recipient,
        String subject,
        String content,
        String templateName,
        Map<String, String> templateVariables,
        Map<String, String> metadata) {

    public ProviderSendRequest {
        templateVariables = templateVariables == null ? Map.of() : Map.copyOf(templateVariables);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
