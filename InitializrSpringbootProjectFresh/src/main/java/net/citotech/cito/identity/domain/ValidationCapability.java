package net.citotech.cito.identity.domain;

import java.util.Locale;

/**
 * Merchant-facing validation capabilities (ISO domain mapping: identity/domain). Merchants
 * activate CPay capabilities like NIN or CREDIT_REPORT; provider-specific operation names stay
 * inside adapters. {@code PHONE_POSSESSION} (CPay OTP) is deliberately distinct from {@code
 * PHONE_OWNERSHIP} (upstream subscriber validation).
 */
public enum ValidationCapability {
    NIN,
    PERSONAL_INFORMATION,
    PHONE_POSSESSION,
    PHONE_OWNERSHIP,
    EMAIL,

    DOCUMENT_OCR,
    DOCUMENT_AUTHENTICITY,
    DOCUMENT_EXPIRY,
    FACE_MATCH,
    LIVENESS,

    ADDRESS,
    TAX_ID,
    BUSINESS_REGISTRATION,
    BENEFICIAL_OWNER,

    KYC,
    KYB,
    KYC_REPORT,

    CREDIT_ENQUIRY,
    CREDIT_REPORT,
    CREDIT_REPORT_PDF,
    CREDIT_SCORE,
    CREDIT_SCORE_CRB,
    CREDIT_SCORE_MNO,
    CREDIT_SCORE_SACCO,
    CREDIT_SCORE_COMBINED;

    public static ValidationCapability fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
