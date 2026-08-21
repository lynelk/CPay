package net.citotech.cito.communication.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Provider-neutral merchant request for one logical communication (ISO domain mapping:
 * communication/domain). The recipient identity, purpose, requested channels, template, and
 * variables describe intent only - channel/provider selection and delivery happen downstream
 * through consent, capability, and routing policy.
 */
public record CommunicationRequest(
        long merchantId,
        String externalReference,
        String recipient,
        CommunicationPurpose purpose,
        List<CommunicationChannel> channels,
        String templateKey,
        Map<String, String> variables,
        boolean fallbackEnabled,
        Instant scheduledAt,
        Instant expiresAt,
        Map<String, String> metadata) {

    public CommunicationRequest {
        if (merchantId <= 0) {
            throw new IllegalArgumentException("merchantId is required.");
        }
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("recipient is required.");
        }
        if (purpose == null) {
            throw new IllegalArgumentException("purpose is required.");
        }
        channels = channels == null || channels.isEmpty() ? List.of() : List.copyOf(channels);
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** Whether the request names at least one channel to attempt. */
    public boolean hasChannels() {
        return !channels.isEmpty();
    }
}
