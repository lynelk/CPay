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
 */
public final class Iso8583MessageSanitizer {

    private static final Pattern MTI = Pattern.compile("^[0-9]{4}$");
    private static final Pattern PAN = Pattern.compile("^[0-9]{12,19}$");

    // Conservative defaults for fields that commonly carry cardholder/security-sensitive data.
    private static final Set<Integer> FULLY_REDACTED_FIELDS = Set.of(14, 35, 45, 52, 53, 55);

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
                        throw new IllegalArgumentException("ISO 8583 data element must be between 2 and 128");
                    }
                    if (field == 2) {
                        sanitized.put(field, maskPan(rawValue));
                    } else if (FULLY_REDACTED_FIELDS.contains(field)) {
                        sanitized.put(field, redactionLabel(field));
                    } else {
                        sanitized.put(field, rawValue);
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
            case 35, 45 -> "[REDACTED-TRACK-DATA]";
            case 52 -> "[REDACTED-PIN-BLOCK]";
            case 53 -> "[REDACTED-SECURITY-CONTROL]";
            case 55 -> "[REDACTED-EMV-DATA]";
            default -> "[REDACTED]";
        };
    }

    public record SanitizedIso8583(String mti, Map<Integer, String> fields) {}
}
