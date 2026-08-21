package net.citotech.cito.identity.domain;

import java.util.Locale;

/**
 * CPay identification-type vocabulary (ISO domain mapping: identity/domain). Provider-specific
 * identifier strings (e.g. gnuGrid's {@code ii_country_id}) are mapped inside adapters; merchant
 * APIs only see these stable values.
 */
public enum IdentificationType {
    NATIONAL_ID,
    PASSPORT,
    TAX_ID,
    BUSINESS_REGISTRATION,
    VAT_NUMBER,
    DRIVING_LICENCE,
    NSSF_NUMBER,
    REFUGEE_NUMBER,
    WORK_PERMIT,
    POLICE_ID,
    MILITARY_ID,
    OTHER;

    public static IdentificationType fromString(String value) {
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
