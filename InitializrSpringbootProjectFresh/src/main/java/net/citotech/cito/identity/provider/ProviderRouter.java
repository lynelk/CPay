package net.citotech.cito.identity.provider;

import java.util.List;
import net.citotech.cito.identity.domain.CheckOutcome;
import net.citotech.cito.identity.domain.ValidationCapability;
import org.springframework.stereotype.Service;

/**
 * Provider router (Track B Phase 8). Hard filters run before any scoring: capability support via
 * {@link ValidationProviderRegistry#byCapability} and health via {@link ProviderHealthMonitor}
 * (an open circuit excludes the provider outright). With a single pool the first eligible
 * provider is deterministic; when multiple adapters exist, {@code preferredProviderCode} (a CPay
 * operator routing preference, never a merchant-specified provider) is honoured first, falling
 * back to registration order. This is deliberately deterministic and explainable — the guide's
 * first-production router must answer "why this provider": capability + healthy + (optional)
 * operator preference.
 */
@Service
public class ProviderRouter {

    private final ValidationProviderRegistry registry;
    private final ProviderHealthMonitor healthMonitor;

    public ProviderRouter(
            ValidationProviderRegistry registry, ProviderHealthMonitor healthMonitor) {
        this.registry = registry;
        this.healthMonitor = healthMonitor;
    }

    /**
     * Returns the eligible adapter for {@code capability}, or throws when no capable + healthy
     * provider exists. {@code preferredProviderCode} is an optional operator routing preference.
     */
    public ValidationProviderAdapter select(
            ValidationCapability capability, String preferredProviderCode) {
        List<ValidationProviderAdapter> capable = registry.byCapability(capability);
        ValidationProviderAdapter preferred = null;
        for (ValidationProviderAdapter adapter : capable) {
            if (adapter.providerCode().equals(preferredProviderCode)) {
                preferred = adapter;
                break;
            }
        }
        if (preferred != null && !healthMonitor.isOpen(preferred.providerCode())) {
            return preferred;
        }
        for (ValidationProviderAdapter adapter : capable) {
            if (adapter == preferred) {
                continue;
            }
            if (!healthMonitor.isOpen(adapter.providerCode())) {
                return adapter;
            }
        }
        throw new ValidationProviderException(
                "ROUTER",
                "PROVIDER_TEMPORARILY_UNAVAILABLE",
                "No capable, healthy validation provider for " + capability);
    }

    /** Records the outcome of an execution so the circuit state stays current. */
    public void record(String providerCode, CheckOutcome outcome) {
        healthMonitor.record(providerCode, outcome);
    }
}
