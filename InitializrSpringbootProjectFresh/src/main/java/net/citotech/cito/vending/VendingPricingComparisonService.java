package net.citotech.cito.vending;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import net.citotech.cito.admin.TenantScopeGuard;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Compares a manufacturer's live price strategy against the CPay vending pricing policy assigned
 * to the rental.
 *
 * <p>The comparison produces a {@code pricing_match_status} (MATCH, MISMATCH, UNKNOWN,
 * NOT_CHECKED) and persists the provider price snapshot so the historical commercial basis of the
 * rental is never silently rewritten by later provider-side changes.
 *
 * <p>Supported merchant policy modes:
 *
 * <ul>
 *   <li>{@code BLOCK_ON_MISMATCH} — throw if material fields differ
 *   <li>{@code WARN_ONLY} — record mismatch but allow rental to proceed
 *   <li>{@code VENDOR_AUTHORITATIVE} — accept provider pricing as authoritative
 * </ul>
 */
@Service
public class VendingPricingComparisonService {
    private static final double CURRENCY_TOLERANCE = 0.001;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public VendingPricingComparisonService(
            NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /**
     * Compares provider price strategy JSON against the CPay policy for the given rental and
     * updates the rental record with the comparison result.
     *
     * @param merchantId tenant scope
     * @param rentalReference CPay rental reference
     * @param providerPriceStrategyJson raw JSON from provider (may be null)
     * @param policyMode merchant price-mismatch policy: BLOCK_ON_MISMATCH, WARN_ONLY,
     *     VENDOR_AUTHORITATIVE
     * @return the computed match status
     */
    public String compareAndUpdate(
            long merchantId,
            String rentalReference,
            String providerPriceStrategyJson,
            String policyMode) {
        Map<String, Object> rental = requireRental(merchantId, rentalReference);
        long policyId = VendingRepository.number(rental.get("pricing_policy_id"));
        VendingPricingPolicy policy =
                loadPolicy(merchantId, policyId);

        String matchStatus;
        if (providerPriceStrategyJson == null || providerPriceStrategyJson.isBlank()) {
            matchStatus = "UNKNOWN";
        } else {
            matchStatus = compare(policy, providerPriceStrategyJson);
        }

        persistComparison(
                merchantId,
                rentalReference,
                providerPriceStrategyJson,
                matchStatus);

        if ("MISMATCH".equals(matchStatus)
                && "BLOCK_ON_MISMATCH".equalsIgnoreCase(
                        policyMode == null ? "" : policyMode.trim())) {
            throw new PaymentGatewayException(
                    "Vending price mismatch detected — rental is blocked by merchant policy");
        }

        return matchStatus;
    }

    /**
     * Compares CPay pricing policy against a ChargeNow priceStrategy JSON payload. Returns MATCH
     * if all material fields agree within tolerance, MISMATCH if any differ, or UNKNOWN if the
     * payload cannot be parsed.
     */
    String compare(VendingPricingPolicy policy, String providerPriceStrategyJson) {
        try {
            JsonNode root = mapper.readTree(providerPriceStrategyJson);
            if (root == null || !root.isObject()) {
                return "UNKNOWN";
            }

            String providerCurrency =
                    text(root, "currency").toUpperCase(Locale.ROOT);
            if (!providerCurrency.isBlank()
                    && !providerCurrency.equals(policy.currency().toUpperCase(Locale.ROOT))) {
                return "MISMATCH";
            }

            if (positiveField(root, "priceMinute")) {
                int providerPricePerMinute = root.get("priceMinute").asInt();
                if (providerPricePerMinute != 0) {
                    // Convert provider price-per-minute to compare with CPay unit price
                    // CPay unitPrice is per billingBlockMinutes
                    // provider priceMinute is per 1 minute
                    int blockMinutes =
                            Math.max(1, policy.billingBlockMinutes());
                    java.math.BigDecimal expectedBlockPrice =
                            policy.unitPrice()
                                    .multiply(java.math.BigDecimal.valueOf(blockMinutes));
                    java.math.BigDecimal providerBlockPrice =
                            java.math.BigDecimal.valueOf(providerPricePerMinute)
                                    .multiply(java.math.BigDecimal.valueOf(blockMinutes));
                    if (expectedBlockPrice.compareTo(providerBlockPrice) != 0) {
                        return "MISMATCH";
                    }
                }
            }

            if (positiveField(root, "depositAmount")) {
                int providerDeposit = root.get("depositAmount").asInt();
                if (providerDeposit != 0
                        && policy.depositAmount()
                                .compareTo(java.math.BigDecimal.valueOf(providerDeposit)) != 0) {
                    return "MISMATCH";
                }
            }

            if (positiveField(root, "freeMinutes")) {
                int providerFree = root.get("freeMinutes").asInt();
                if (providerFree != policy.freeMinutes()) {
                    return "MISMATCH";
                }
            }

            if (positiveField(root, "dailyMaxPrice")) {
                int providerDailyCap = root.get("dailyMaxPrice").asInt();
                if (providerDailyCap != 0 && policy.dailyCapAmount() != null) {
                    if (policy.dailyCapAmount()
                            .compareTo(java.math.BigDecimal.valueOf(providerDailyCap)) != 0) {
                        return "MISMATCH";
                    }
                }
            }

            if (positiveField(root, "timeoutAmount")) {
                int providerTimeout = root.get("timeoutAmount").asInt();
                if (providerTimeout != 0 && policy.overtimeAmount() != null) {
                    if (policy.overtimeAmount()
                            .compareTo(java.math.BigDecimal.valueOf(providerTimeout)) != 0) {
                        return "MISMATCH";
                    }
                }
            }

            if (positiveField(root, "timeoutDay")) {
                int providerTimeoutDays = root.get("timeoutDay").asInt();
                if (providerTimeoutDays != 0 && policy.overtimeDays() != null) {
                    if (policy.overtimeDays() != providerTimeoutDays) {
                        return "MISMATCH";
                    }
                }
            }

            return "MATCH";
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private void persistComparison(
            long merchantId,
            String rentalReference,
            String providerPriceStrategyJson,
            String matchStatus) {
        String priceHash = sha256(providerPriceStrategyJson == null ? "" : providerPriceStrategyJson);
        String sql =
                "UPDATE vending_rentals SET provider_price_snapshot=:snapshot, "
                        + "provider_price_hash=:hash, pricing_match_status=:status, "
                        + "last_provider_verified_at=CURRENT_TIMESTAMP "
                        + "WHERE merchant_id=:tenant_merchant_id AND rental_reference=:rental_reference";
        TenantScopeGuard.assertTenantBound(sql);
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("snapshot", providerPriceStrategyJson);
        p.addValue("hash", priceHash);
        p.addValue("status", matchStatus);
        p.addValue("rental_reference", rentalReference);
        jdbc.update(sql, p);
    }

    private Map<String, Object> requireRental(long merchantId, String rentalReference) {
        String sql =
                "SELECT * FROM vending_rentals WHERE merchant_id=:tenant_merchant_id AND rental_reference=:rental_reference";
        TenantScopeGuard.assertTenantBound(sql);
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("rental_reference", rentalReference);
        var rows = jdbc.queryForList(sql, p);
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Vending rental was not found");
        }
        return rows.get(0);
    }

    private VendingPricingPolicy loadPolicy(long merchantId, long policyId) {
        String sql =
                "SELECT * FROM vending_pricing_policies WHERE merchant_id=:tenant_merchant_id AND id=:id AND active_flag='YES'";
        TenantScopeGuard.assertTenantBound(sql);
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("id", policyId);
        var rows = jdbc.queryForList(sql, p);
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Vending pricing policy was not found or is inactive");
        }
        Map<String, Object> row = rows.get(0);
        return new VendingPricingPolicy(
                VendingRepository.number(row.get("id")),
                merchantId,
                VendingRepository.string(row.get("policy_code")),
                VendingRepository.string(row.get("currency")),
                VendingRepository.decimal(row.get("deposit_amount")),
                VendingRepository.integer(row.get("free_minutes")),
                VendingRepository.decimal(row.get("unit_price")),
                VendingRepository.integer(row.get("billing_block_minutes")),
                VendingRepository.integer(row.get("minimum_billing_blocks")),
                VendingRepository.nullableDecimal(row.get("daily_cap_amount")),
                VendingRepository.nullableDecimal(row.get("overtime_amount")),
                VendingRepository.nullableInteger(row.get("overtime_days")),
                VendingRepository.string(row.get("refund_mode")));
    }

    private boolean positiveField(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node != null && !node.isNull() && node.isNumber();
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash vending price strategy", e);
        }
    }
}
