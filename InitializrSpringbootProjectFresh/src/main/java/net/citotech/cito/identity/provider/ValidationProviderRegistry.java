package net.citotech.cito.identity.provider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.citotech.cito.identity.domain.ValidationCapability;
import org.springframework.stereotype.Component;

/**
 * Central registry of upstream validation providers (Track B Phase 8; ISO domain mapping:
 * identity/provider). Adapters are collected from the Spring context and indexed by provider code.
 * The router uses {@link #byCapability(ValidationCapability)} eligibility lookup; direct
 * {@link #find(String)} is for diagnostics/admin, never the merchant API. Provider codes are
 * stable database identities — Java class names are never exposed.
 */
@Component
public class ValidationProviderRegistry {

    private final Map<String, ValidationProviderAdapter> byCode;

    public ValidationProviderRegistry(List<ValidationProviderAdapter> adapters) {
        Map<String, ValidationProviderAdapter> index = new LinkedHashMap<>();
        for (ValidationProviderAdapter adapter : adapters) {
            index.put(adapter.providerCode(), adapter);
        }
        this.byCode = Map.copyOf(index);
    }

    public Optional<ValidationProviderAdapter> find(String providerCode) {
        return Optional.ofNullable(byCode.get(providerCode));
    }

    /** Adapters able to serve {@code capability} — the router's eligibility pool. */
    public List<ValidationProviderAdapter> byCapability(ValidationCapability capability) {
        return byCode.values().stream()
                .filter(adapter -> adapter.capabilities().contains(capability))
                .toList();
    }

    public Set<String> codes() {
        return byCode.keySet();
    }
}
