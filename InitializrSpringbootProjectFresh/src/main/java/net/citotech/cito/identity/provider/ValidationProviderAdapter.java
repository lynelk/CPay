package net.citotech.cito.identity.provider;

import java.util.Set;
import net.citotech.cito.identity.domain.ValidationCapability;

/**
 * Provider-neutral validation SPI (ISO domain mapping: identity/provider). Adapters translate
 * CPay requests into provider calls and normalize responses into evidence. They never make the
 * final KYC, credit, or identity decision — CPay's policy engine owns that.
 */
public interface ValidationProviderAdapter {

    /** Stable database provider identity (e.g. {@code GNUGRID_CRB}); not a Java class name. */
    String providerCode();

    /** Capabilities this adapter can satisfy. */
    Set<ValidationCapability> capabilities();

    /** Eligibility filter applied by the router before scoring. */
    default boolean supports(CheckContext context) {
        return capabilities().contains(context.capability());
    }

    /**
     * Execute a check. Synchronous for adapters whose provider answers inline; asynchronous
     * flows use {@link #poll(String)} or callback ingestion upstream of this SPI.
     */
    ProviderCheckResult execute(ProviderCheckRequest request);

    /** Poll a provider by its external reference. Throws if the provider has no polling flow. */
    default ProviderCheckResult poll(String externalReference) {
        throw new UnsupportedOperationException(
                "Provider " + providerCode() + " has no polling flow");
    }
}
