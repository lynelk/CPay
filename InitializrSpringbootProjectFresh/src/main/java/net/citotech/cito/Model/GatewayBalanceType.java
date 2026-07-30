package net.citotech.cito.Model;

/**
 * Type-safe replacement for the stringly-typed {@code String[]} pairs on {@link Balance} (audit
 * J9). {@link Balance#getBalanceTypeByGatewayId} still returns {@code String[]} for the ~50
 * existing call sites that pattern-match on it, but new code should use this enum instead of
 * literal column-name strings.
 */
public enum GatewayBalanceType {
    MTN_MOMO("MTNMoMoPaymentGateway", "mtn_momo", "mtnmm_balance", "UGX MTN MM", "UGX"),
    AIRTEL_MONEY(
            "AirtelMoneyPaymentGateway",
            "airtel_money",
            "airtelmm_balance",
            "UGX AIRTEL MM",
            "UGX"),
    AIRTEL_OPENAPI(
            "AirtelMoneyOpenApiPaymentGateway",
            "airtel_open_api",
            "airtelmm_balance",
            "UGX AIRTEL MM",
            "UGX"),
    SAFARICOM_MPESA(
            "SafariComPaymentGateway", "safaricom_mpesa", "safaricom_balance", "KES MPESA", "KES"),
    SMS("SmsGateway", "sms", "sms_balance", "UGX SMS", "UGX");

    private final String gatewayId;
    private final String channelCode;
    private final String columnName;
    private final String label;
    private final String currencyCode;

    GatewayBalanceType(
            String gatewayId,
            String channelCode,
            String columnName,
            String label,
            String currencyCode) {
        this.gatewayId = gatewayId;
        this.channelCode = channelCode;
        this.columnName = columnName;
        this.label = label;
        this.currencyCode = currencyCode;
    }

    public String gatewayId() {
        return gatewayId;
    }

    public String channelCode() {
        return channelCode;
    }

    public String columnName() {
        return columnName;
    }

    public String label() {
        return label;
    }

    /** ISO currency code this balance is denominated in (audit A3: currency-as-data). */
    public String currencyCode() {
        return currencyCode;
    }

    public static GatewayBalanceType fromGatewayId(String gatewayId) {
        for (GatewayBalanceType type : values()) {
            if (type.gatewayId.equals(gatewayId)) {
                return type;
            }
        }
        return null;
    }

    public static GatewayBalanceType fromColumnName(String columnName) {
        for (GatewayBalanceType type : values()) {
            if (type.columnName.equals(columnName)) {
                return type;
            }
        }
        return null;
    }
}
