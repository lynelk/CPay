package net.citotech.cito.security;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Centralised allowlist of SQL column names that are permitted in dynamic
 * {@code WHERE column LIKE :value} search clauses.
 *
 * <p>Every controller that builds search SQL from user-supplied category names
 * must call {@link #validate(String, String)} before appending the column name
 * to the query string.  The <em>value</em> itself is still bound via a named
 * parameter to prevent injection of the search term.
 */
public final class ColumnAllowlist {

    /**
     * Per-table permitted search columns.
     * Key = logical table identifier used in controllers.
     */
    private static final Map<String, Map<String, String>> TABLE_COLUMNS;

    static {
        Map<String, Map<String, String>> m = new HashMap<>();

        m.put("admins", mapOf("name", "email", "phone", "status", "created_on", "updated_on"));

        m.put("merchant_admins", mapOf("name", "email", "phone", "status",
                "created_on", "updated_on"));

        m.put("merchants", mapOf("name", "account_number", "status", "account_type",
                "created_on", "updated_on", "short_name"));

        m.put("merchant_transactions_log", mapOf(
                "gateway_id", "status", "payer_number", "tx_type",
                "tx_unique_id", "tx_gateway_ref", "tx_merchant_ref",
                "charging_method", "created_on", "updated_on", "merchant_id"));

        m.put("audit_trail", mapOf("user_name", "user_id", "action",
                "created_on", "updated_on"));

        // Catch-all fallback (used when table is not specified).
        m.put("default", mapOf("name", "email", "phone", "status",
                "created_on", "updated_on", "gateway_id", "tx_type",
                "payer_number", "user_name", "user_id", "action",
                "account_number", "short_name"));

        TABLE_COLUMNS = Collections.unmodifiableMap(m);
    }

    private ColumnAllowlist() {}

    /**
     * Validates that {@code column} is allowed for the given {@code table}.
     *
     * @param table  logical table identifier (one of the keys above)
     * @param column user-supplied column name to validate
     * @return the validated column name, safe to interpolate into SQL
     * @throws IllegalArgumentException if {@code column} is not in the allowlist
     */
    public static String validate(String table, String column) {
        if (column == null || column.isEmpty()) {
            throw new IllegalArgumentException("Search column must not be empty");
        }
        String normalized = column.toLowerCase();
        Map<String, String> allowed = TABLE_COLUMNS.getOrDefault(table, TABLE_COLUMNS.get("default"));
        String canonical = allowed.get(normalized);
        if (canonical == null) {
            throw new IllegalArgumentException(
                    "Search by column '" + column + "' is not permitted on table '" + table + "'");
        }
        return canonical;
    }

    /**
     * Convenience overload that uses the {@code default} allowlist.
     */
    public static @org.springframework.lang.NonNull String validate(String column) {
        return validate("default", column);
    }

    public static Optional<String> merchantUserColumn(String column) {
        try {
            return Optional.of(validate("merchant_admins", column));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
    // -------------------------------------------------------------------------

    private static Map<String, String> mapOf(String... values) {
        Map<String, String> m = new HashMap<>();
        for (String value : values) {
            String normalized = value.toLowerCase();
            m.put(normalized, normalized);
        }
        return Collections.unmodifiableMap(m);
    }
}
