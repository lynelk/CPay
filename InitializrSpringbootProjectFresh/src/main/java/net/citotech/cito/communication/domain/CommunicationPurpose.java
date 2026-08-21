package net.citotech.cito.communication.domain;

import java.util.Locale;

/**
 * Communication purpose (ISO domain mapping: communication/domain). Purpose drives consent,
 * fallback, and priority policy: marketing requires explicit consent while transactional/security
 * messages follow separate policy lanes. New paths must parse purpose at the API boundary and
 * never fall back to a free-form string.
 */
public enum CommunicationPurpose {
    PAYMENT_RECEIPT,
    PAYOUT_NOTICE,
    SECURITY,
    OTP,
    SERVICE,
    COMPLIANCE,
    KYC,
    COLLECTION_REMINDER,
    MARKETING,
    SUPPORT;

    public static CommunicationPurpose fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Marketing is the only purpose that always requires explicit recipient consent. */
    public boolean requiresExplicitConsent() {
        return this == MARKETING;
    }
}
