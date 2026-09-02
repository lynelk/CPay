package net.citotech.cito.sharedprovider;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.citotech.cito.SettingsRegistry;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Idempotently provisions the CPay Shared Payments offer for active interactive merchants. */
@Service
public class SharedProviderDefaultEntitlementService {
    private static final List<String> CHANNELS = List.of("mtn_momo", "airtel_open_api");

    private final NamedParameterJdbcTemplate jdbc;

    public SharedProviderDefaultEntitlementService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelayString = "${cpay.shared-provider.provision-interval-ms:300000}")
    public void provisionActiveMerchants() {
        if (!SettingsRegistry.getBoolean("shared_provider_default_enabled", jdbc)) return;
        List<Long> merchantIds =
                jdbc.query(
                        "SELECT id FROM merchants WHERE status='ACTIVE' AND account_number NOT LIKE 'CITO-%'",
                        Map.of(), (rs, rowNum) -> rs.getLong("id"));
        merchantIds.forEach(this::provisionMerchant);
    }

    @Transactional
    public void provisionMerchant(long merchantId) {
        if (!SettingsRegistry.getBoolean("shared_provider_default_enabled", jdbc)) return;
        Integer eligible =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM merchants WHERE id=:id AND status='ACTIVE' AND account_number NOT LIKE 'CITO-%'",
                        new MapSqlParameterSource("id", merchantId), Integer.class);
        if (eligible == null || eligible == 0) return;
        grantApi(merchantId, "MOBILE_MONEY_PAYIN");

        String country =
                SettingsRegistry.getString("shared_provider_default_country", jdbc)
                        .trim()
                        .toUpperCase(Locale.ROOT);
        String currency =
                SettingsRegistry.getString("shared_provider_default_currency", jdbc)
                        .trim()
                        .toUpperCase(Locale.ROOT);
        if (country.isEmpty() || currency.isEmpty()) {
            throw new PaymentGatewayException(
                    "Shared-provider default country and currency must be configured");
        }
        boolean activatePayout =
                SettingsRegistry.getBoolean("shared_provider_default_payout_enabled", jdbc);
        for (String channel : CHANNELS) {
            insertDefault(
                    merchantId,
                    channel,
                    country,
                    currency,
                    "COLLECT",
                    "ACTIVE",
                    SettingsRegistry.getDecimal(
                            "shared_provider_default_collection_per_transaction_limit", jdbc),
                    SettingsRegistry.getDecimal(
                            "shared_provider_default_collection_daily_limit", jdbc));
            insertDefault(
                    merchantId,
                    channel,
                    country,
                    currency,
                    "PAYOUT",
                    activatePayout ? "ACTIVE" : "PENDING",
                    SettingsRegistry.getDecimal(
                            "shared_provider_default_payout_per_transaction_limit", jdbc),
                    SettingsRegistry.getDecimal(
                            "shared_provider_default_payout_daily_limit", jdbc));
        }
    }

    public Map<String, Object> merchantAccess(long merchantId, String environment) {
        provisionMerchant(merchantId);
        String env = required(environment, "environment").toUpperCase(Locale.ROOT);
        List<Map<String, Object>> rails =
                jdbc.queryForList(
                        "SELECT e.channel_code AS channelCode, e.country_code AS countryCode,"
                                + " e.currency_code AS currencyCode, e.operation, e.status,"
                                + " e.per_transaction_limit AS perTransactionLimit, e.daily_limit AS dailyLimit,"
                                + " COALESCE(u.approved_amount,0) AS usedToday,"
                                + " CASE WHEN pc.status='ACTIVE' THEN 'ACTIVE' ELSE COALESCE(pc.status,'NOT_CONFIGURED') END AS credentialStatus"
                                + " FROM shared_provider_entitlements e"
                                + " LEFT JOIN shared_provider_daily_usage u ON u.entitlement_id=e.id"
                                + " AND u.usage_date=CURRENT_DATE AND u.operation='AUTHORIZED'"
                                + " LEFT JOIN platform_channel_credentials pc ON pc.channel_code=e.channel_code"
                                + " AND pc.environment=e.environment AND pc.country_code=e.country_code"
                                + " AND pc.currency_code=e.currency_code"
                                + " WHERE e.merchant_id=:merchant AND e.environment=:environment"
                                + " ORDER BY e.channel_code, e.operation",
                        new MapSqlParameterSource()
                                .addValue("merchant", merchantId)
                                .addValue("environment", env));
        for (Map<String, Object> rail : rails) {
            BigDecimal daily = decimal(rail.get("dailyLimit"));
            BigDecimal used = decimal(rail.get("usedToday"));
            rail.put(
                    "remainingToday",
                    daily == null ? null : daily.subtract(used).max(BigDecimal.ZERO));
            rail.put(
                    "ready",
                    "ACTIVE".equals(rail.get("status"))
                            && "ACTIVE".equals(rail.get("credentialStatus")));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("channelCode", "cpay_shared");
        result.put("displayName", "CPay Shared Payments");
        result.put(
                "description",
                "Use CPay-managed MTN and Airtel connections until your own credentials are approved.");
        result.put("environment", env);
        result.put("status", overallStatus(rails));
        result.put("credentialSource", SharedProviderAccessService.PLATFORM_SHARED);
        result.put("providerCredentialsRequired", false);
        result.put("rails", rails);
        return result;
    }

    private void insertDefault(
            long merchantId,
            String channel,
            String country,
            String currency,
            String operation,
            String status,
            BigDecimal perTransaction,
            BigDecimal daily) {
        jdbc.update(
                "INSERT IGNORE INTO shared_provider_entitlements"
                        + " (merchant_id,channel_code,environment,country_code,currency_code,operation,status,"
                        + " per_transaction_limit,daily_limit,requested_by,approved_by,approved_at,notes)"
                        + " VALUES (:merchant,:channel,'PRODUCTION',:country,:currency,:operation,:status,"
                        + " :per_transaction,:daily,'SYSTEM_DEFAULT',"
                        + " CASE WHEN :status='ACTIVE' THEN 'SYSTEM_DEFAULT' ELSE NULL END,"
                        + " CASE WHEN :status='ACTIVE' THEN CURRENT_TIMESTAMP(6) ELSE NULL END,"
                        + " 'Automatically provisioned CPay Shared Payments access')",
                new MapSqlParameterSource()
                        .addValue("merchant", merchantId)
                        .addValue("channel", channel)
                        .addValue("country", country)
                        .addValue("currency", currency)
                        .addValue("operation", operation)
                        .addValue("status", status)
                        .addValue("per_transaction", perTransaction)
                        .addValue("daily", daily));
    }

    private void grantApi(long merchantId, String api) {
        jdbc.update(
                "UPDATE merchants SET allowed_apis=CASE"
                        + " WHEN allowed_apis IS NULL OR TRIM(allowed_apis)='' THEN :api"
                        + " ELSE CONCAT(TRIM(TRAILING ',' FROM allowed_apis),',',:api) END"
                        + " WHERE id=:merchant AND FIND_IN_SET(:api,REPLACE(COALESCE(allowed_apis,''),' ',''))=0",
                new MapSqlParameterSource().addValue("merchant", merchantId).addValue("api", api));
    }

    private String overallStatus(List<Map<String, Object>> rails) {
        if (rails.stream().anyMatch(row -> Boolean.TRUE.equals(row.get("ready")))) return "READY";
        if (rails.stream().anyMatch(row -> "ACTIVE".equals(row.get("status")))) {
            return "AWAITING_PLATFORM_CREDENTIALS";
        }
        return rails.isEmpty() ? "NOT_AVAILABLE_IN_ENVIRONMENT" : "PENDING_APPROVAL";
    }

    private BigDecimal decimal(Object value) {
        if (value == null) return null;
        return value instanceof BigDecimal amount ? amount : new BigDecimal(String.valueOf(value));
    }

    private String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new PaymentGatewayException(field + " is required");
        }
        return value.trim();
    }
}
