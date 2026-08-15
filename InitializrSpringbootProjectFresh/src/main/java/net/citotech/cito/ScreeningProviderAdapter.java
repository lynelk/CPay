package net.citotech.cito;

/**
 * Provider-neutral screening adapter contract.
 *
 * <p>Implementations must not embed provider credentials. Live provider adapters should be
 * activated by configuration only after certification and credential provisioning are complete.
 */
public interface ScreeningProviderAdapter {
    String providerCode();

    ScreeningProviderAdapterRegistry.ScreeningResult screen(
            ScreeningProviderAdapterRegistry.ScreeningRequest request,
            ScreeningProviderAdapterRegistry.ProviderConfig providerConfig);
}
