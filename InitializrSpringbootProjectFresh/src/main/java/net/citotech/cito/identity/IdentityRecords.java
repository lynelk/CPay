package net.citotech.cito.identity;

import java.time.Instant;

/** Shared value types for the identity-verification package. */
public final class IdentityRecords {

    private IdentityRecords() {}

    /**
     * Input to an identity verification. The identity number is carried only to the selected
     * connector and is never echoed in logs or errors. {@code fullName} and {@code msisdn} are
     * optional confirmatory fields.
     */
    public record IdentityVerificationRequest(
            String requestReference,
            Long merchantId,
            String identityType,
            String country,
            String identityNumber,
            String fullName,
            String msisdn) {
        public IdentityVerificationRequest {
            if (requestReference == null || requestReference.isBlank()) {
                throw new IllegalArgumentException("requestReference is required.");
            }
            if (merchantId == null || merchantId <= 0) {
                throw new IllegalArgumentException("merchantId is required.");
            }
            if (identityType == null || identityType.trim().isEmpty()) {
                throw new IllegalArgumentException("identityType is required.");
            }
            if (country == null || country.trim().isEmpty()) {
                throw new IllegalArgumentException("country is required.");
            }
            if (identityNumber == null || identityNumber.trim().isEmpty()) {
                throw new IllegalArgumentException("identityNumber is required.");
            }
            identityType = identityType.trim().toUpperCase();
            country = country.trim().toUpperCase();
            identityNumber = identityNumber.trim().toUpperCase();
        }

        /** Backward-compatible constructor for the original Uganda NIN integration. */
        public IdentityVerificationRequest(
                String requestReference, Long merchantId, String nin, String fullName, String msisdn) {
            this(requestReference, merchantId, "NIN", "UG", nin, fullName, msisdn);
        }

        /** Backward-compatible accessor used by the original GnuGrid NIN adapter and tests. */
        public String nin() {
            return identityNumber;
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
