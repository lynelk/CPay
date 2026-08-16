package net.citotech.cito.money;

import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;

/**
 * Currency-aware money metadata (backlog §2.6). Replaces hardcoded universal money scales so a
 * regional/multi-currency platform does not assume one scale for every currency.
 *
 * <p>The ledger keeps an internal settlement scale (4 decimal places) so that FX and percentage
 * calculations do not lose precision, while display-scale currency metadata (ISO 4217 minor units)
 * governs how fractional amounts are represented for a given currency.
 */
public final class CurrencyMetadata {

    /** Internal ledger arithmetic scale - finer than any ISO minor unit to avoid rounding loss. */
    public static final int SETTLEMENT_SCALE = 4;

    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    /** ISO 4217 minor-unit digits for common CPay currencies. */
    private static final Map<String, Integer> MINOR_UNITS =
            Map.of(
                    "UGX", 0,
                    "KES", 2,
                    "TZS", 2,
                    "RWF", 0,
                    "NGN", 2,
                    "GHS", 2,
                    "ZAR", 2,
                    "USD", 2,
                    "EUR", 2,
                    "GBP", 2);

    private CurrencyMetadata() {
        // static registry
    }

    /**
     * Returns the ISO 4217 minor-unit digit scale for the currency, defaulting to 2 for currencies
     * not explicitly listed.
     */
    public static int minorUnitScale(String currency) {
        if (currency == null || currency.trim().isEmpty()) {
            return 2;
        }
        String code = currency.trim().toUpperCase(Locale.ROOT);
        return MINOR_UNITS.getOrDefault(code, 2);
    }

    /** Normalizes a currency code to upper-case, preserving null as null. */
    public static String normalize(String currency) {
        if (currency == null) {
            return null;
        }
        String code = currency.trim().toUpperCase(Locale.ROOT);
        if (code.isEmpty()) {
            throw new IllegalArgumentException("currency is required");
        }
        return code;
    }
}
