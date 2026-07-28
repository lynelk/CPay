package net.citotech.cito;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.citotech.cito.Model.Setting;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Typed, self-documenting registry over the generic {@code settings} table (audit O5). The
 * {@code settings} table itself stays an untyped name/setting_value/setting_group/description
 * store - {@link Common#getSettings} still returns a raw {@link Setting} row for every existing
 * call site and continues to work unchanged. What was missing was a single place that says, for
 * each setting name actually read anywhere in this codebase: what type it is, what a missing or
 * invalid stored value should safely fall back to, and what it's for - previously each call site
 * duplicated its own boolean/int parsing and its own hardcoded default, so a typo'd name or a
 * corrupted value silently fell back to whatever that one call site happened to hardcode.
 *
 * <p>This registry is purely additive: register a real setting name once here, then read it with
 * {@link #getBoolean}, {@link #getInt}, {@link #getDecimal}, or {@link #getString} instead of
 * hand-rolling the parsing again. Reading an unregistered name is treated as a programming error
 * (typo'd setting name, or a setting nobody documented) and throws immediately rather than
 * guessing a type; reading a registered name whose stored value is missing or fails to parse never
 * throws - it logs a warning and falls back to that entry's own documented default, because a
 * money-adjacent request should degrade to a safe default rather than fail on bad configuration
 * data.
 */
public final class SettingsRegistry {

    public enum SettingType {
        BOOLEAN,
        INTEGER,
        DECIMAL,
        STRING
    }

    public record Entry(String name, SettingType type, String defaultValue, String description, String group) {
    }

    private static final Logger LOGGER = Logger.getLogger(SettingsRegistry.class.getName());

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

    static {
        // Gateway / ledger routing (Common.java) - see Common.useMerchantProviderCredentials
        // and Common.resolveGatewayAccounts.
        register("use_merchant_provider_credentials", SettingType.BOOLEAN, "false", "gateway",
            "When true, merchants use their own configured provider credentials and channel "
                + "accounts instead of the shared float/suspense/revenue accounts below.");
        register("float_stock_account", SettingType.STRING, "", "ledger",
            "Merchant account number used as the shared float/stock account for gateway fund "
                + "postings when use_merchant_provider_credentials is false.");
        register("suspense_account", SettingType.STRING, "", "ledger",
            "Merchant account number used as the shared suspense account for in-flight gateway "
                + "postings when use_merchant_provider_credentials is false.");
        register("revenue_account", SettingType.STRING, "", "ledger",
            "Merchant account number used as the shared revenue account for gateway fee postings "
                + "when use_merchant_provider_credentials is false.");
        register("sms_revenue_account", SettingType.STRING, "", "ledger",
            "Merchant account number credited for SMS gateway revenue.");

        // Gateway mode / simulation toggles (DoPayGateway.java, StartupApplicationListener.java).
        register("application_settings_state", SettingType.STRING, "sandbox", "gateway",
            "Global gateway mode: 'sandbox' or 'production'. Production mode requires the gw_* "
                + "provider URLs and credentials to be fully configured.");
        register("simulate_transactions", SettingType.BOOLEAN, "false", "gateway",
            "When true, gateway calls are simulated instead of calling real provider APIs "
                + "(used for demos and non-production testing).");
        register("gw_airtelmoney_use_open_api", SettingType.BOOLEAN, "false", "gateway",
            "When true, Airtel Money transactions are routed through the Airtel OpenAPI adapter "
                + "instead of the legacy Airtel Money API.");
        register("gw_mtn_api_env", SettingType.STRING, "sandbox", "gateway",
            "MTN MoMo API environment selector used to pick which gw_mtn_api_* credential set to "
                + "use ('mtnuganda' for production, anything else falls back to sandbox).");
        register("gw_safaricom_api_version", SettingType.STRING, "v2", "gateway",
            "Safaricom M-Pesa API version to call ('v2' or 'v3').");

        // Merchant self-service / developer sandbox (MerchantEnvironmentService.java).
        register("production_transaction_limit_enabled", SettingType.BOOLEAN, "true", "merchant",
            "When true, merchants are capped at production_transaction_limit_count "
                + "production-environment transactions per day.");
        register("production_transaction_limit_count", SettingType.INTEGER, "10", "merchant",
            "Maximum number of production-environment transactions a merchant may submit per "
                + "day while production_transaction_limit_enabled is true.");
        register("developer_sandbox_base_url", SettingType.STRING, "https://sandbox.cpay.example", "merchant",
            "Base URL shown to merchants in the developer sandbox guide for sandbox-environment "
                + "API calls.");
        register("developer_production_base_url", SettingType.STRING, "https://api.cpay.example", "merchant",
            "Base URL shown to merchants in the developer sandbox guide for production-environment "
                + "API calls.");
        register("developer_sandbox_merchant_number", SettingType.STRING, "1000000", "merchant",
            "Fallback merchant number shown in the developer sandbox guide when the caller has "
                + "none configured yet.");
        register("developer_sandbox_idempotency_hours", SettingType.INTEGER, "24", "merchant",
            "Sandbox idempotency key retention window shown to merchants in the developer "
                + "sandbox guide, in hours.");
        register("developer_sandbox_retention_days", SettingType.INTEGER, "7", "merchant",
            "Sandbox transaction data retention window shown to merchants in the developer "
                + "sandbox guide, in days.");

        // Float balance alerting (FloatAlertScheduler.java).
        register("float_alert_email", SettingType.STRING, "", "float",
            "Recipient email address for float balance threshold alerts; alerting is skipped "
                + "entirely while this is blank.");
        register("float_alert_mtn_min", SettingType.DECIMAL, "0", "float",
            "Minimum MTN MoMo float balance before an alert email is sent.");
        register("float_alert_airtel_min", SettingType.DECIMAL, "0", "float",
            "Minimum Airtel Money float balance before an alert email is sent.");
        register("float_alert_safaricom_min", SettingType.DECIMAL, "0", "float",
            "Minimum Safaricom M-Pesa float balance before an alert email is sent.");
    }

    private static void register(String name, SettingType type, String defaultValue, String group,
            String description) {
        ENTRIES.put(name, new Entry(name, type, defaultValue, description, group));
    }

    /** Never returns null - unregistered names resolve to an empty {@link Optional}. */
    public static Optional<Entry> lookup(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(ENTRIES.get(name.trim()));
    }

    public static boolean isKnown(String name) {
        return lookup(name).isPresent();
    }

    public static List<Entry> all() {
        return List.copyOf(ENTRIES.values());
    }

    /**
     * True for setting names that look like they hold a credential (password/pin/token/api key)
     * rather than plain configuration. None of the entries registered above match this today -
     * the real provider credentials in this codebase (gw_*_api_password, gw_*_api_pin,
     * gw_*_api_*_key, ...) are deliberately left out of this registry entirely - but admin
     * surfaces that list registry entries alongside their live values (see
     * {@code SettingsRegistryController}) call this as a defensive check before ever printing a
     * stored value, in case a future entry is added for a credential-shaped setting.
     */
    public static boolean isSecretLike(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase();
        return lower.contains("password") || lower.contains("_pin") || lower.endsWith("pin")
            || lower.contains("_secret") || lower.contains("_token") || lower.contains("_key")
            || lower.contains("apikey") || lower.contains("api_key");
    }

    /**
     * Resolves a BOOLEAN setting. Recognizes true/false, 1/0, and yes/no (case-insensitive,
     * trimmed). A missing row or an unparseable stored value logs a warning and falls back to the
     * entry's documented default - it never throws for bad data, only for a caller asking about a
     * name this registry doesn't know about.
     */
    public static boolean getBoolean(String name, NamedParameterJdbcTemplate jdbcTemplate) {
        Entry entry = requireEntry(name, SettingType.BOOLEAN);
        String raw = rawValue(name, jdbcTemplate);
        Optional<Boolean> parsed = parseBoolean(raw);
        if (parsed.isPresent()) {
            return parsed.get();
        }
        if (raw != null) {
            LOGGER.log(Level.WARNING, "Setting '{0}' has a non-boolean stored value '{1}'; "
                + "falling back to documented default '{2}'.", new Object[]{name, raw, entry.defaultValue()});
        }
        return parseBoolean(entry.defaultValue()).orElse(false);
    }

    /**
     * Resolves an INTEGER setting. A missing row or an unparseable stored value logs a warning
     * and falls back to the entry's documented default.
     */
    public static int getInt(String name, NamedParameterJdbcTemplate jdbcTemplate) {
        Entry entry = requireEntry(name, SettingType.INTEGER);
        String raw = rawValue(name, jdbcTemplate);
        if (raw != null) {
            try {
                return Integer.parseInt(raw.trim());
            } catch (NumberFormatException e) {
                LOGGER.log(Level.WARNING, "Setting '{0}' has a non-integer stored value '{1}'; "
                    + "falling back to documented default '{2}'.", new Object[]{name, raw, entry.defaultValue()});
            }
        }
        return Integer.parseInt(entry.defaultValue());
    }

    /**
     * Resolves a DECIMAL setting as a {@link BigDecimal} (never a floating-point type - see
     * Claude.md's "avoid floating-point arithmetic for money" rule). A missing row or an
     * unparseable stored value logs a warning and falls back to the entry's documented default.
     */
    public static BigDecimal getDecimal(String name, NamedParameterJdbcTemplate jdbcTemplate) {
        Entry entry = requireEntry(name, SettingType.DECIMAL);
        String raw = rawValue(name, jdbcTemplate);
        if (raw != null) {
            try {
                return new BigDecimal(raw.trim());
            } catch (NumberFormatException e) {
                LOGGER.log(Level.WARNING, "Setting '{0}' has a non-decimal stored value '{1}'; "
                    + "falling back to documented default '{2}'.", new Object[]{name, raw, entry.defaultValue()});
            }
        }
        return new BigDecimal(entry.defaultValue());
    }

    /**
     * Resolves a STRING setting. A missing row or a blank stored value falls back to the entry's
     * documented default (no parse failure is possible for a string, so no warning is logged).
     */
    public static String getString(String name, NamedParameterJdbcTemplate jdbcTemplate) {
        Entry entry = requireEntry(name, SettingType.STRING);
        String raw = rawValue(name, jdbcTemplate);
        return raw == null || raw.isBlank() ? entry.defaultValue() : raw.trim();
    }

    private static Entry requireEntry(String name, SettingType expectedType) {
        Entry entry = ENTRIES.get(name);
        if (entry == null) {
            String message = "Unregistered setting '" + name + "' - add it to SettingsRegistry "
                + "before reading it as typed data (typo'd setting names must fail loudly, not "
                + "silently guess a type and a default).";
            LOGGER.log(Level.SEVERE, message);
            throw new IllegalArgumentException(message);
        }
        if (entry.type() != expectedType) {
            String message = "Setting '" + name + "' is registered as " + entry.type()
                + " in SettingsRegistry, not " + expectedType + " - fix the caller or the registry entry.";
            LOGGER.log(Level.SEVERE, message);
            throw new IllegalArgumentException(message);
        }
        return entry;
    }

    private static String rawValue(String name, NamedParameterJdbcTemplate jdbcTemplate) {
        Setting setting = Common.getSettings(name, jdbcTemplate);
        return setting == null ? null : setting.getSetting_value();
    }

    private static Optional<Boolean> parseBoolean(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        if (trimmed.equalsIgnoreCase("true") || trimmed.equals("1") || trimmed.equalsIgnoreCase("yes")) {
            return Optional.of(Boolean.TRUE);
        }
        if (trimmed.equalsIgnoreCase("false") || trimmed.equals("0") || trimmed.equalsIgnoreCase("no")) {
            return Optional.of(Boolean.FALSE);
        }
        return Optional.empty();
    }

    private SettingsRegistry() {
    }
}
