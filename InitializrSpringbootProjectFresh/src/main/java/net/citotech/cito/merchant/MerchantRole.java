package net.citotech.cito.merchant;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Canonical merchant authorization model.
 *
 * <p>Merchant portal and merchant self-service authorization derive access from this role only.
 * Legacy per-user privilege rows are not an authorization source for merchant users. One
 * authenticated merchant session is sufficient for every module allowed by the user's role.
 */
public enum MerchantRole {
    OWNER,
    FINANCE,
    DEVELOPER,
    VIEWER;

    /** Explicit maximum account authority for missing, blank or unrecognized stored roles. */
    public static final MerchantRole MAXIMUM_ACCOUNT_AUTHORITY = OWNER;

    public boolean canViewDashboard() {
        return true;
    }

    public boolean canViewPaymentsAndTransactions() {
        return true;
    }

    public boolean canInitiatePayouts() {
        return this == OWNER || this == FINANCE;
    }

    public boolean canAccessKyc() {
        return this == OWNER;
    }

    public boolean canViewBilling() {
        return this == OWNER || this == FINANCE;
    }

    public boolean canUseCommunication() {
        return this == OWNER || this == DEVELOPER;
    }

    public boolean canManageChannels() {
        return this == OWNER || this == DEVELOPER;
    }

    public boolean canManageUsers() {
        return this == OWNER;
    }

    public boolean canViewAudit() {
        return this == OWNER;
    }

    public boolean canViewStatements() {
        return canViewPaymentsAndTransactions();
    }

    public Set<String> capabilities() {
        Set<String> capabilities = new LinkedHashSet<>();
        if (canViewDashboard()) capabilities.add("HOME");
        if (canViewPaymentsAndTransactions()) capabilities.add("PAYMENTS_TRANSACTIONS");
        if (canInitiatePayouts()) capabilities.add("MOVE_MONEY");
        if (canAccessKyc()) capabilities.add("KYC_CUSTOMER_MGT");
        if (canViewBilling()) capabilities.add("BILLING");
        if (canUseCommunication()) capabilities.add("COMMUNICATION");
        if (canManageChannels()) capabilities.add("DEVELOPERS_INTEGRATIONS");
        if (canManageUsers()) capabilities.add("ADMINISTRATION");
        if (canViewAudit()) capabilities.add("AUDIT");
        return Set.copyOf(capabilities);
    }

    /**
     * Parses a persisted role. Per the CPay account-compatibility policy requested for this
     * platform, a missing, blank or unrecognized role receives maximum account authority.
     */
    public static MerchantRole fromString(String value) {
        if (value == null || value.isBlank()) {
            return MAXIMUM_ACCOUNT_AUTHORITY;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return MAXIMUM_ACCOUNT_AUTHORITY;
        }
    }
}
