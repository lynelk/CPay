package net.citotech.cito.financialmessage;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Structural validator for ISO 9362 Business Identifier Codes (BICs).
 *
 * <p>This class validates the 8/11-character shape used by CPay adapter boundaries. It does not
 * verify assignment, activation status, institution ownership, service eligibility, or registry
 * membership. Production workflows that depend on those properties must use an authoritative BIC
 * directory or counterparty validation source and retain that evidence separately.
 */
public final class BicValidator {

    private static final Pattern STRUCTURE =
            Pattern.compile("^[A-Z]{4}[A-Z]{2}[A-Z0-9]{2}(?:[A-Z0-9]{3})?$");

    private BicValidator() {}

    public static boolean isStructurallyValid(String value) {
        if (value == null) {
            return false;
        }
        String normalized = compact(value);
        return STRUCTURE.matcher(normalized).matches();
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("BIC is required");
        }
        String normalized = compact(value);
        if (!STRUCTURE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("BIC must be a structurally valid 8 or 11 character ISO 9362 identifier");
        }
        return normalized;
    }

    public static String institutionCode(String value) {
        return normalize(value).substring(0, 4);
    }

    public static String countryCode(String value) {
        return normalize(value).substring(4, 6);
    }

    public static String locationCode(String value) {
        return normalize(value).substring(6, 8);
    }

    public static String branchCode(String value) {
        String normalized = normalize(value);
        return normalized.length() == 11 ? normalized.substring(8) : null;
    }

    private static String compact(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
