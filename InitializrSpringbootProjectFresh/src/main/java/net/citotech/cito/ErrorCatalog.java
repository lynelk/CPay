package net.citotech.cito;

import java.util.HashMap;
import java.util.Map;

/**
 * Stable machine-readable error metadata for the legacy numeric error codes returned by
 * {@link GeneralException#getError}, matching the shape documented in
 * {@code Docs/Error-catalog.md} (audit D3): a stable code name, a category, and whether the
 * error is safe to retry.
 */
public final class ErrorCatalog {
    public static final String DOCS_BASE_URL = "https://github.com/lynelk/CPay/blob/main/Docs/Error-catalog.md";

    public enum Category {
        AUTHENTICATION,
        VALIDATION,
        BUSINESS_RULE,
        PROVIDER,
        SYSTEM
    }

    public record Entry(String stableCode, Category category, boolean retryable) {
        public String categoryName() {
            return category.name().toLowerCase();
        }

        public String docsUrl() {
            return DOCS_BASE_URL + "#" + stableCode.toLowerCase();
        }
    }

    private static final Entry DEFAULT_UNKNOWN = new Entry("SYSTEM_UNAVAILABLE", Category.SYSTEM, true);

    private static final Map<String, Entry> ENTRIES = new HashMap<>();

    static {
        register("100", "REQUEST_INVALID", Category.VALIDATION, false);
        register("101", "REQUEST_INVALID", Category.VALIDATION, false);
        register("102", "SYSTEM_UNAVAILABLE", Category.SYSTEM, true);
        register("103", "AUTH_CREDENTIALS_INVALID", Category.AUTHENTICATION, false);
        register("104", "ACCOUNT_NOT_FOUND", Category.VALIDATION, false);
        register("105", "AUTH_VERIFICATION_TIMEOUT", Category.AUTHENTICATION, true);
        register("106", "AUTH_VERIFICATION_CODE_INVALID", Category.AUTHENTICATION, false);
        register("107", "AUTH_SESSION_REQUIRED", Category.AUTHENTICATION, false);
        register("108", "RESOURCE_ALREADY_EXISTS", Category.VALIDATION, false);
        register("109", "RESOURCE_NOT_FOUND", Category.VALIDATION, false);
        register("110", "AUTHORIZATION_DENIED", Category.AUTHENTICATION, false);
        register("111", "PAYMENT_INSUFFICIENT_FUNDS", Category.BUSINESS_RULE, false);
        register("112", "SYSTEM_MISCONFIGURED", Category.SYSTEM, false);
        register("113", "REQUEST_INVALID", Category.VALIDATION, false);
        register("114", "REQUEST_INVALID", Category.VALIDATION, false);
        register("115", "AUTH_CREDENTIALS_MISSING", Category.AUTHENTICATION, false);
        register("116", "AUTH_SIGNATURE_INVALID", Category.AUTHENTICATION, false);
        register("117", "SYSTEM_MISCONFIGURED", Category.SYSTEM, false);
        register("118", "REQUEST_INVALID", Category.VALIDATION, false);
        register("119", "ACCOUNT_NOT_ACTIVE", Category.BUSINESS_RULE, false);
        register("120", "AUTHORIZATION_DENIED", Category.AUTHENTICATION, false);
        register("121", "PAYMENT_DUPLICATE_REFERENCE", Category.BUSINESS_RULE, false);
        register("122", "AUTH_SIGNATURE_INVALID", Category.AUTHENTICATION, false);
        register("123", "REQUEST_INVALID", Category.VALIDATION, false);
        register("124", "REQUEST_INVALID", Category.VALIDATION, false);
        register("125", "REQUEST_INVALID", Category.VALIDATION, false);
        register("126", "REQUEST_INVALID", Category.VALIDATION, false);
        register("127", "SYSTEM_MISCONFIGURED", Category.SYSTEM, false);
        register("128", "PAYMENT_STATE_LOCKED", Category.BUSINESS_RULE, false);
        register("129", "REQUEST_INVALID", Category.VALIDATION, false);
        register("130", "PAYMENT_STATE_LOCKED", Category.BUSINESS_RULE, false);
        register("131", "REQUEST_INVALID", Category.VALIDATION, false);
        register("132", "REQUEST_INVALID", Category.VALIDATION, false);
        register("133", "SYSTEM_MISCONFIGURED", Category.SYSTEM, false);
        register("134", "REQUEST_INVALID", Category.VALIDATION, false);
        register("135", "REQUEST_INVALID", Category.VALIDATION, false);
        register("136", "SYSTEM_MISCONFIGURED", Category.SYSTEM, false);
        register("137", "ACCOUNT_NOT_ACTIVE", Category.BUSINESS_RULE, false);
        register("138", "AUTH_CREDENTIALS_INVALID", Category.AUTHENTICATION, false);
        register("139", "AUTHORIZATION_DENIED", Category.AUTHENTICATION, false);
        register("140", "SYSTEM_MISCONFIGURED", Category.SYSTEM, false);
        register("141", "SYSTEM_MISCONFIGURED", Category.SYSTEM, false);
        register("142", "PROVIDER_DECLINED", Category.PROVIDER, false);
        register("143", "PROVIDER_DECLINED", Category.PROVIDER, false);
        register("144", "PAYMENT_DUPLICATE_REFERENCE", Category.BUSINESS_RULE, false);
        register("145", "RATE_LIMITED", Category.SYSTEM, true);
        register("146", "REQUEST_INVALID", Category.VALIDATION, false);
        register("147", "AUTH_EMAIL_NOT_VERIFIED", Category.AUTHENTICATION, false);
        register("148", "RISK_AUTHORIZATION_DECLINED", Category.BUSINESS_RULE, false);
        register("149", "AUTH_STEP_UP_MFA_REQUIRED", Category.AUTHENTICATION, false);
    }

    private static void register(String legacyCode, String stableCode, Category category, boolean retryable) {
        ENTRIES.put(legacyCode, new Entry(stableCode, category, retryable));
    }

    /** Never returns null - unmapped codes fall back to a generic retryable system error entry. */
    public static Entry lookup(String legacyCode) {
        return ENTRIES.getOrDefault(legacyCode, DEFAULT_UNKNOWN);
    }

    private ErrorCatalog() {
    }
}
