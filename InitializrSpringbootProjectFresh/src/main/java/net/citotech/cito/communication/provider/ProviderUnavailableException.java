package net.citotech.cito.communication.provider;

import net.citotech.cito.communication.domain.CommunicationChannel;

/**
 * Raised when the provider registry has no adapter for a requested provider+channel (ISO domain
 * mapping: communication/provider). The dispatcher maps this to an honest REJECTED delivery row -
 * never a silent "delivered".
 */
public class ProviderUnavailableException extends RuntimeException {

    public ProviderUnavailableException(String providerCode, CommunicationChannel channel) {
        super(
                "No provider adapter registered for provider "
                        + providerCode
                        + " channel "
                        + channel);
    }
}
