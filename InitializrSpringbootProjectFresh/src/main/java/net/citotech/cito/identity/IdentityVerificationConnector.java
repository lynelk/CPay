package net.citotech.cito.identity;

import java.util.Map;

/**
 * Contract for third-party identity-verification providers.
 *
 * <p>S5 pilot: a GnuGrid NIN adapter is the first implementation. Providers are stateless with
 * respect to CPay's database - they translate an {@link
 * IdentityRecords.IdentityVerificationRequest} into an {@link IdentityRecords.VerifiedIdentity}
 * result and (for async providers) accept a callback. Adapters must never persist raw PII
 * themselves; any storage is owned by {@link IdentityVerificationService}.
 */
public interface IdentityVerificationConnector {

    /** Stable machine code, for example {@code gnugrid}. */
    String providerCode();

    /** Whether this provider supports the synchronous request/response flow. */
    boolean supportsSync();

    /** Whether this provider can deliver results via an outbound callback. */
    boolean supportsAsync();

    /**
     * Synchronously verifies an identity.
     *
     * @throws IdentityVerificationException when the provider is unreachable, misconfigured, or
     *     rejects the request; the error message must never contain raw PII.
     */
    IdentityRecords.VerifiedIdentity verify(IdentityRecords.IdentityVerificationRequest request);

    /**
     * Parses a provider callback payload into a verification result. Implementations must
     * defensively validate the payload; {@link #validateCallbackHeaders(Map)} is invoked by the
     * callback endpoint and must be passed the raw callback headers it returned true for.
     */
    IdentityRecords.VerifiedIdentity parseCallback(
            String callbackBody, Map<String, String> callbackHeaders);

    /**
     * Validates the authenticity of a callback before {@link #parseCallback} is trusted.
     *
     * @return true when the callback is authentic, or when this provider carries no verification
     *     material to check against (mirrors {@code PaymentChannelAdapter#verifyCallback}).
     */
    boolean validateCallbackHeaders(Map<String, String> callbackHeaders);
}
