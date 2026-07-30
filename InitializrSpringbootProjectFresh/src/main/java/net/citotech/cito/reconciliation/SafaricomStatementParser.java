package net.citotech.cito.reconciliation;

import java.util.HashMap;
import java.util.Map;

/**
 * Safaricom M-Pesa statement parser (audit O1). M-Pesa statement exports commonly use "Receipt No."
 * and "Completion Time" rather than the generic "reference"/"date" aliases the other providers
 * share — added here as extra, case-insensitive aliases (not a replacement) since this is a
 * genuinely different, provider-specific column convention. This is based on commonly documented
 * M-Pesa statement formats, not a verified real file from this integration — confirm against an
 * actual Safaricom statement sample before relying on it for a production import.
 */
public class SafaricomStatementParser extends AbstractTabularStatementParser {
    public SafaricomStatementParser() {
        super("SAFARICOM", "safaricom_mpesa");
    }

    @Override
    protected Map<String, String[]> columnAliases() {
        Map<String, String[]> aliases = new HashMap<>(super.columnAliases());
        aliases.put(
                "providerReference",
                concat(
                        aliases.get("providerReference"),
                        "receipt no.",
                        "receipt no",
                        "receipt number"));
        aliases.put("transactionDate", concat(aliases.get("transactionDate"), "completion time"));
        aliases.put("amount", concat(aliases.get("amount"), "paid in", "amount paid"));
        return aliases;
    }

    private String[] concat(String[] base, String... extra) {
        String[] result = new String[base.length + extra.length];
        System.arraycopy(base, 0, result, 0, base.length);
        System.arraycopy(extra, 0, result, base.length, extra.length);
        return result;
    }
}
