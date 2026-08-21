package net.citotech.cito.communication.provider;

import net.citotech.cito.communication.domain.CommunicationChannel;

/**
 * Channel-neutral provider boundary for the CPay Communications Gateway (ISO domain mapping:
 * communication/provider). Every channel adapter (SMS wrappers, WhatsApp, future push/email)
 * implements this interface so the dispatcher and future router can send through any provider by
 * {@code providerCode}+channel instead of growing a switch per channel. Provider classes must
 * never leak into merchant APIs: {@code providerCode} is the stable database identifier, never the
 * Java class name.
 */
public interface CommunicationProviderAdapter {

    /** Stable provider identifier used by {@code communication_providers} and routing rules. */
    String providerCode();

    /** Channel this adapter serves. */
    CommunicationChannel channel();

    /** Capability flags of this adapter. */
    ProviderCapabilities capabilities();

    /** Executes one send and returns a normalized, PII-safe result. */
    ProviderSendResult send(ProviderSendRequest request);

    /** Health signal; defaults to UNKNOWN until the provider health service observes traffic. */
    default ProviderHealth health() {
        return new ProviderHealth(ProviderHealth.State.UNKNOWN, null, null);
    }
}
