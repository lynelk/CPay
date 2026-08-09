package net.citotech.cito.admin;

/**
 * Canonical feature-flag keys, documented and seeded in V36 ({@code feature_flags} / {@code
 * merchant_feature_flags}). Feature consumers reference these constants instead of re-typing the
 * string so a rename/typo surfaces in one place.
 */
public final class FeatureKeys {
    public static final String BALANCE_MONITORING = "balance-monitoring";
    public static final String IDENTITY_GNUGRID = "identity-gnugrid";
    public static final String PAYOUT_CONTROLS_CONFIG = "payout-controls-config";
    public static final String COMPLIANCE_DASHBOARD = "compliance-dashboard";
    public static final String KYB_REVIEW = "kyb-review";
    public static final String PROVIDER_CERTIFICATION_DASHBOARD =
            "provider-certification-dashboard";
    public static final String REGULATOR_REPORTING_UI = "regulator-reporting-ui";
    public static final String TREASURY_DASHBOARD = "treasury-dashboard";
    public static final String VENDING_PLATFORM = "vending-platform";

    private FeatureKeys() {}
}
