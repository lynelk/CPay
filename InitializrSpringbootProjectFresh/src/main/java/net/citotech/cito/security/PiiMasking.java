package net.citotech.cito.security;

/**
 * PII masking for logs and non-diagnostic UI surfaces (compliance roadmap: mask payer/payee numbers
 * in logs and non-diagnostic views; keep hashes for matching).
 *
 * <p>This is a deliberately small, dependency-free utility. It never reverses a mask and never
 * stores raw values - {@link #maskMsdn} keeps only the last four digits of a mobile-money number
 * (the operator-control convention), {@link #maskEmail} keeps the first character and the domain,
 * and every other value shortens to the first two characters plus "***". The full raw number is
 * only ever available from {@code merchant_transactions_log} (masked in logs) and EFRIS reads it
 * in-process at send time (never logged).
 */
public final class PiiMasking {

    private PiiMasking() {}

    /** Mask a mobile-money MSISDN: {@code 256770000001} -> {@code 25677****0001}. */
    public static String maskMsdn(String value) {
        if (isBlank(value)) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 6) {
            return "***";
        }
        return trimmed.substring(0, 4) + "***" + trimmed.substring(trimmed.length() - 4);
    }

    /** Mask an email: {@code jane@example.com} -> {@code j***@example.com}. */
    public static String maskEmail(String value) {
        if (isBlank(value)) {
            return value;
        }
        String trimmed = value.trim();
        int at = trimmed.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return trimmed.charAt(0) + "***" + trimmed.substring(at);
    }

    /** Mask an account/reference/number generically: keeps first two chars + "***". */
    public static String maskGeneric(String value) {
        if (isBlank(value)) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 2) {
            return "***";
        }
        return trimmed.substring(0, 2) + "***";
    }

    /** Mask a merchant name in list/diagnostic surfaces: {@code Acme Ltd} -> {@code Ac*** L.}. */
    public static String maskName(String value) {
        if (isBlank(value)) {
            return value;
        }
        String trimmed = value.trim();
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 1) {
            return maskGeneric(parts[0]);
        }
        return maskGeneric(parts[0]) + " " + parts[parts.length - 1].charAt(0) + ".";
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
