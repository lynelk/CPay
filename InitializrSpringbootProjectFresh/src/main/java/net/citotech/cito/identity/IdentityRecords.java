package net.citotech.cito.identity;

import java.time.Instant;

/** Shared value types for the identity-verification package. */
public final class IdentityRecords {

    private IdentityRecords() {}

    /**
     * Input to an identity verification. {@code fullName} and {@code msisdn} are optional
     * confirmatory fields (GnuGrid may match on NIN + name/phone); the NIN itself is required. None
     * of these fields are ever echoed back in logs or errors.
     */
    public record IdentityVerificationRequest(
            String requestReference, Long merchantId, String nin, String fullName, String msisdn) {
        public IdentityVerificationRequest {
            if (requestReference == null || requestReference.isBlank()) {
                throw new IllegalArgumentException("requestReference is required.");
            }
            if (merchantId == null || merchantId <= 0) {
                throw new IllegalArgumentException("merchantId is required.");
            }
            if (nin == null || nin.trim().isEmpty()) {
                throw new IllegalArgumentException("NIN is required.");
            }
        }
    }

    /** Result of a verification, produced by a connector and persisted by the service. */
    public record VerifiedIdentity(
            String requestReference,
            String providerCode,
            String verificationStatus,
            String providerReference,
            boolean match,
            String fullNameMasked,
            Instant verifiedAt,
            Instant expiresAt,
            String rawProviderResult) {

        public static VerifiedIdentity matched(
                String requestReference,
                String providerCode,
                String providerReference,
                String fullNameMasked,
                Instant verifiedAt,
                Instant expiresAt,
                String rawProviderResult) {
            return new VerifiedIdentity(
                    requestReference,
                    providerCode,
                    "VERIFIED",
                    providerReference,
                    true,
                    fullNameMasked,
                    verifiedAt,
                    expiresAt,
                    rawProviderResult);
        }

        public static VerifiedIdentity failed(
                String requestReference,
                String providerCode,
                String providerReference,
                String rawProviderResult) {
            return new VerifiedIdentity(
                    requestReference,
                    providerCode,
                    "NOT_VERIFIED",
                    providerReference,
                    false,
                    null,
                    null,
                    null,
                    rawProviderResult);
        }
    }
}
