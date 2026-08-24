package net.citotech.cito.identity;

import java.util.Map;

/** Contract for third-party identity-verification providers. */
public interface IdentityVerificationConnector {

    /** Stable machine code, for example {@code gnugrid}. */
    String providerCode();

    /** Whether this provider supports the synchronous request/response flow. */
    boolean supportsSync();

    /** Whether this provider can deliver results via an outbound callback. */
    boolean supportsAsync();

    /**
     * Whether this configured connector may verify the requested document type/country. Providers
     * can override this as their official data coverage expands. The conservative default preserves
     * the original Uganda NIN behavior.
     */
    default boolean supports(String identityType, String country) {
        return "NIN".equalsIgnoreCase(identityType) && "UG".equalsIgnoreCase(country);
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
