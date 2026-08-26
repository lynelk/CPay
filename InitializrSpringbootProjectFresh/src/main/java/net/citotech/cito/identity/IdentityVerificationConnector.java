package net.citotech.cito.identity;

import java.util.Map;
import java.util.Set;

/** Contract for third-party identity-verification providers. */
public interface IdentityVerificationConnector {

    /** Stable machine code, for example {@code gnugrid}. */
    String providerCode();

    /** Whether this provider supports the synchronous request/response flow. */
    boolean supportsSync();

    /** Whether this provider can deliver results via an outbound callback. */
    boolean supportsAsync();

    /** Document types this configured connector may authoritatively verify. */
    default Set<String> supportedIdentityTypes() {
        return Set.of("NIN");
    }

    /** ISO alpha-2 countries this configured connector may authoritatively verify. */
    default Set<String> supportedCountries() {
        return Set.of("UG");
    }

    /**
     * Whether this connector can satisfy the synchronous verification operation for the requested
     * document type/country. Async-only providers remain visible through the capability methods but
     * are not selected by the synchronous /verify flow.
     */
    default boolean supports(String identityType, String country) {
        return supportsSync()
                && identityType != null
                && country != null
                && supportedIdentityTypes().stream().anyMatch(identityType::equalsIgnoreCase)
                && supportedCountries().stream().anyMatch(country::equalsIgnoreCase);
    }

    /**
     * Synchronously verifies an identity.
     *
     * @throws IdentityVerificationException when the provider is unreachable, misconfigured, or
     *     rejects the request; the error message must never contain raw PII.
     */
    IdentityRecords.VerifiedIdentity verify(IdentityRecords.IdentityVerificationRequest request);

    /** Parses a provider callback payload into a verification result. */
    IdentityRecords.VerifiedIdentity parseCallback(
            String callbackBody, Map<String, String> callbackHeaders);

    /** Validates callback authenticity before the callback result is trusted. */
    boolean validateCallbackHeaders(Map<String, String> callbackHeaders);
}
