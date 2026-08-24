package net.citotech.cito.financialmessage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Defensive validation/redaction boundary for diagnostic representations of ISO 8583 messages.
 *
 * <p>This is deliberately not a packager/codec. Network-specific field definitions, encodings,
 * MAC/PIN handling and message rules belong to the controlled counterparty profile. This helper
 * prevents generic diagnostics from becoming a convenient archive of card/security data.
 * Unknown/unclassified fields are redacted by default.
 */
public final class Iso8583MessageSanitizer {

    private static final Pattern MTI = Pattern.compile("^[0-9]{4}$");
    private static final Pattern PAN = Pattern.compile("^[0-9]{12,19}$");

    // Minimal standard fields considered safe enough for generic diagnostics. A network-specific
    // profile may be stricter, but generic code must never broaden this list implicitly.
    private static final Set<Integer> SAFE_DIAGNOSTIC_FIELDS =
            Set.of(3, 4, 7, 11, 12, 13, 18, 22, 24, 25, 39, 49);

    // Commonly sensitive fields get explicit labels; everything else outside the safe allowlist is
    // still redacted as unclassified rather than copied verbatim.
    private static final Set<Integer> FULLY_REDACTED_FIELDS =
            Set.of(14, 34, 35, 45, 52, 53, 55, 102, 103);

    private Iso8583MessageSanitizer() {}

    public static SanitizedIso8583 sanitize(String mti, Map<Integer, String> fields) {
        if (mti == null || !MTI.matcher(mti).matches()) {
            throw new IllegalArgumentException("ISO 8583 MTI must contain exactly four decimal digits");
        }
        if (fields == null) {
            throw new IllegalArgumentException("ISO 8583 fields are required");
        }

        Map<Integer, String> sanitized = new LinkedHashMap<>();
        fields.forEach(
                (field, rawValue) -> {
                    if (field == null || field < 2 || field > 128) {
                        throw new IllegalArgumentException(
                                "ISO 8583 data element must be between 2 and 128");
                    }
                    if (field == 2) {
                        sanitized.put(field, maskPan(rawValue));
                    } else if (FULLY_REDACTED_FIELDS.contains(field)) {
                        sanitized.put(field, redactionLabel(field));
                    } else if (SAFE_DIAGNOSTIC_FIELDS.contains(field)) {
                        sanitized.put(field, rawValue);
                    } else {
                        sanitized.put(field, "[REDACTED-UNCLASSIFIED-DE-" + field + "]");
                    }
                });

        return new SanitizedIso8583(mti, Collections.unmodifiableMap(sanitized));
    }

    public static String maskPan(String pan) {
        if (pan == null || pan.isBlank()) {
            return "[REDACTED-PAN]";
        }
        String compact = pan.trim();
        if (!PAN.matcher(compact).matches()) {
            return "[REDACTED-PAN]";
        }
        String lastFour = compact.substring(compact.length() - 4);
        return "************" + lastFour;
    }

    private static String redactionLabel(int field) {
        return switch (field) {
            case 14 -> "[REDACTED-EXPIRY]";
            case 34 -> "[REDACTED-EXTENDED-PAN]";
            case 35, 45 -> "[REDACTED-TRACK-DATA]";
            case 52 -> "[REDACTED-PIN-BLOCK]";
            case 53 -> "[REDACTED-SECURITY-CONTROL]";
            case 55 -> "[REDACTED-EMV-DATA]";
            case 102, 103 -> "[REDACTED-ACCOUNT-IDENTIFIER]";
            default -> "[REDACTED]";
        };
    }

    public record SanitizedIso8583(String mti, Map<Integer, String> fields) {}
}
