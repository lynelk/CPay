package net.citotech.cito.Model;

/**
 * Type-safe replacement for the stringly-typed {@code String[]} pairs on {@link Balance}
 * (audit J9). {@link Balance#getBalanceTypeByGatewayId} still returns {@code String[]} for the
 * ~50 existing call sites that pattern-match on it, but new code should use this enum instead of
 * literal column-name strings.
 */
public enum GatewayBalanceType {
    MTN_MOMO("MTNMoMoPaymentGateway", "mtnmm_balance", "UGX MTN MM"),
    AIRTEL_MONEY("AirtelMoneyPaymentGateway", "airtelmm_balance", "UGX AIRTEL MM"),
    AIRTEL_OPENAPI("AirtelMoneyOpenApiPaymentGateway", "airtelmm_balance", "UGX AIRTEL MM"),
    SAFARICOM_MPESA("SafariComPaymentGateway", "safaricom_balance", "KES MPESA"),
    SMS("SmsGateway", "sms_balance", "UGX SMS");

    private final String gatewayId;
    private final String columnName;
    private final String label;

    GatewayBalanceType(String gatewayId, String columnName, String label) {
        this.gatewayId = gatewayId;
        this.columnName = columnName;
        this.label = label;
    }

    public String gatewayId() {
        return gatewayId;
    }

    public String columnName() {
        return columnName;
    }

    public String label() {
        return label;
    }

    public static GatewayBalanceType fromGatewayId(String gatewayId) {
        for (GatewayBalanceType type : values()) {
            if (type.gatewayId.equals(gatewayId)) {
                return type;
            }
        }
        return null;
    }
}
