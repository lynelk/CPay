package net.citotech.cito.merchant;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Canonical merchant authorization model.
 *
 * <p>Merchant portal and merchant self-service authorization must derive access from this role
 * only. Legacy per-user privilege rows are no longer an authorization source for merchant users.
 * One authenticated merchant session is therefore sufficient for every module allowed by the
 * user's role.
 */
public enum MerchantRole {
    OWNER,
    FINANCE,
    DEVELOPER,
    VIEWER;

    /** Explicit maximum account authority used for missing/legacy/unrecognized role values. */
    public static final MerchantRole MAXIMUM_ACCOUNT_AUTHORITY = OWNER;

    public boolean canViewDashboard() {
        return true;
    }

    /** All merchant roles can inspect their tenant's transaction and statement history. */
    public boolean canViewPaymentsAndTransactions() {
        return true;
    }

    /** Initiate money-moving actions such as payouts, refunds and batch payments. */
    public boolean canInitiatePayouts() {
        return this == OWNER || this == FINANCE;
    }

    /** Read or update legal/KYC identity for the merchant account. */
    public boolean canAccessKyc() {
        return this == OWNER;
    }

    /** View merchant pricing, metered usage and billing information. */
    public boolean canViewBilling() {
        return this == OWNER || this == FINANCE;
    }

    /** Send/manage merchant communications and inspect channel delivery operations. */
    public boolean canUseCommunication() {
        return this == OWNER || this == DEVELOPER;
    }

    /** Manage channel credentials, API keys, webhook/config registration and sandbox settings. */
    public boolean canManageChannels() {
        return this == OWNER || this == DEVELOPER;
    }

    /** Manage merchant team members and account-level settings. */
    public boolean canManageUsers() {
        return this == OWNER;
    }

    /** View the merchant's own audit history. */
    public boolean canViewAudit() {
        return this == OWNER;
    }

    /** Backward-compatible semantic alias used by statement endpoints. */
    public boolean canViewStatements() {
        return canViewPaymentsAndTransactions();
    }

    /**
     * Returns stable portal capability keys consumed by the merchant SPA. Backend authorization
     * still checks the boolean capability methods above, so hiding a menu never becomes the
     * security boundary.
     */
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
     * Parses a persisted role. Per the CPay account-compatibility policy, a missing, blank or
     * unrecognized role resolves to the maximum account authority rather than silently reducing an
     * existing merchant user's access during migration or rollback/roll-forward scenarios.
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
