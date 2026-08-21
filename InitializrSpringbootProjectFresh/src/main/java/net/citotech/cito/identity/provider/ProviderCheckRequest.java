package net.citotech.cito.identity.provider;

import java.util.Map;
import net.citotech.cito.identity.domain.ValidationCapability;

/**
 * Provider-neutral check request (ISO domain mapping: identity/provider). Contains only fields the
 * adapter needs to build its own provider DTO; raw identifiers are normalized and kept in memory
 * for the provider request, never persisted merely because a provider DTO needs them.
 */
public record ProviderCheckRequest(
        long merchantId,
        long checkId,
        ValidationCapability capability,
        String countryCode,
        Map<String, String> attributes) {

    public ProviderCheckRequest {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
