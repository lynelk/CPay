package net.citotech.cito.payout;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.citotech.cito.admin.FeatureFlagService;
import net.citotech.cito.admin.FeatureKeys;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin config surface for payout risk controls (audit finding: {@code payout_controls} could only
 * be managed by SQL, so operators had no self-service way to set the limits the v2 payout path
 * enforces). Writes use the same {@code merchant_id + channel_code + currency + country} compound
 * key {@link PayoutControlService#evaluate} reads, so a row saved here is enforceable immediately
 * by the money path.
 *
 * <p>The surface is gated by the {@code payout-controls-config} feature flag (V36) like the other
 * S5/S6 pilot surfaces. A zero/blank amount limit is stored as NULL (no limit) so a row can be
 * eased on and off without deleting it; {@code enabledFlag=NO} preserves the historical
 * immediate-execution behaviour.
 */
@Service
public class PayoutConfigService {

    private static final int MAX_LIST = 500;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final FeatureFlagService featureFlagService;

    public PayoutConfigService(
            NamedParameterJdbcTemplate jdbcTemplate, FeatureFlagService featureFlagService) {
        this.jdbcTemplate = jdbcTemplate;
        this.featureFlagService = featureFlagService;
    }

    public record ConfigRequest(
            Long merchantId,
            String channelCode,
            String currency,
            String country,
            BigDecimal dailyAmountLimit,
            BigDecimal monthlyAmountLimit,
            BigDecimal perTransactionLimit,
            Integer beneficiaryVelocityLimit,
            String approvalRequiredFlag,
            String enabledFlag,
            String changedBy) {}

    /** Lists configured control rows, optionally filtered to one merchant (by id). */
    public List<Map<String, Object>> list(String merchantFilter) {
        ensureEnabled();
        MapSqlParameterSource p = new MapSqlParameterSource();
        String sql =
                "SELECT id, merchant_id, channel_code, currency, country, daily_amount_limit, "
                        + "monthly_amount_limit, per_transaction_limit, beneficiary_velocity_limit, "
                        + "approval_required_flag, enabled_flag, created_at, updated_at "
                        + "FROM payout_controls ";
        if (merchantFilter != null && !merchantFilter.isBlank()) {
            sql += "WHERE merchant_id=:merchant_id ";
            p.addValue("merchant_id", parseMerchantId(merchantFilter));
        }
        sql += "ORDER BY merchant_id, channel_code, currency LIMIT " + MAX_LIST;
        return jdbcTemplate.queryForList(sql, p);
    }

    /**
     * Inserts or updates a payout-control row. Null amount limits clear the previous value (a
     * zero/blank limit is stored as NULL = no limit); a disabled row ({@code enabledFlag=NO}) is
     * skipped by the evaluation path, preserving immediate execution.
     */
    @Transactional
    public Map<String, Object> upsert(ConfigRequest request) {
        ensureEnabled();
        validate(request);
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchant_id", request.merchantId());
        p.addValue("channel_code", normalize(request.channelCode()));
        p.addValue("currency", normalize(request.currency()));
        p.addValue("country", normalizeOrDefault(request.country(), "UG"));
        p.addValue("daily_amount_limit", nullableAmount(request.dailyAmountLimit()));
        p.addValue("monthly_amount_limit", nullableAmount(request.monthlyAmountLimit()));
        p.addValue("per_transaction_limit", nullableAmount(request.perTransactionLimit()));
        p.addValue(
                "beneficiary_velocity_limit", nullableVelocity(request.beneficiaryVelocityLimit()));
        p.addValue("approval_required_flag", normalizeFlag(request.approvalRequiredFlag(), "NO"));
        p.addValue("enabled_flag", normalizeFlag(request.enabledFlag(), "YES"));
        jdbcTemplate.update(
                "INSERT INTO payout_controls "
                        + "(merchant_id, channel_code, currency, country, daily_amount_limit, "
                        + "monthly_amount_limit, per_transaction_limit, beneficiary_velocity_limit, "
                        + "approval_required_flag, enabled_flag) "
                        + "VALUES (:merchant_id, :channel_code, :currency, :country, "
                        + ":daily_amount_limit, :monthly_amount_limit, :per_transaction_limit, "
                        + ":beneficiary_velocity_limit, :approval_required_flag, :enabled_flag) "
                        + "ON DUPLICATE KEY UPDATE "
                        + "daily_amount_limit=VALUES(daily_amount_limit), "
                        + "monthly_amount_limit=VALUES(monthly_amount_limit), "
                        + "per_transaction_limit=VALUES(per_transaction_limit), "
                        + "beneficiary_velocity_limit=VALUES(beneficiary_velocity_limit), "
                        + "approval_required_flag=VALUES(approval_required_flag), "
                        + "enabled_flag=VALUES(enabled_flag)",
                p);
        return row(
                requireLong(request.merchantId()),
                normalize(request.channelCode()),
                normalize(request.currency()));
    }

    /** Deletes a control row by id. Returns rows affected (0 = not found). */
    @Transactional
    public int delete(long id) {
        ensureEnabled();
        return jdbcTemplate.update(
                "DELETE FROM payout_controls WHERE id=:id", new MapSqlParameterSource("id", id));
    }

    private Map<String, Object> row(long merchantId, String channelCode, String currency) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT id, merchant_id, channel_code, currency, country, daily_amount_limit, "
                                + "monthly_amount_limit, per_transaction_limit, beneficiary_velocity_limit, "
                                + "approval_required_flag, enabled_flag, updated_at "
                                + "FROM payout_controls WHERE merchant_id=:merchant_id "
                                + "AND channel_code=:channel_code AND currency=:currency",
                        new MapSqlParameterSource()
                                .addValue("merchant_id", merchantId)
                                .addValue("channel_code", channelCode)
                                .addValue("currency", currency));
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private void validate(ConfigRequest request) {
        if (request == null || request.merchantId() == null || request.merchantId() <= 0) {
            throw new PaymentGatewayException(
                    "merchantId is required for payout control configuration");
        }
        if (blank(request.channelCode()) || blank(request.currency())) {
            throw new PaymentGatewayException(
                    "channelCode and currency are required for payout control configuration");
        }
        if (request.beneficiaryVelocityLimit() != null && request.beneficiaryVelocityLimit() < 0) {
            throw new PaymentGatewayException("beneficiaryVelocityLimit must be zero or greater");
        }
        String approval = normalizeFlag(request.approvalRequiredFlag(), "NO");
        if (!"YES".equals(approval) && !"NO".equals(approval)) {
            throw new PaymentGatewayException("approvalRequiredFlag must be YES or NO");
        }
        String enabled = normalizeFlag(request.enabledFlag(), "YES");
        if (!"YES".equals(enabled) && !"NO".equals(enabled)) {
            throw new PaymentGatewayException("enabledFlag must be YES or NO");
        }
        requireNonNegative(request.dailyAmountLimit(), "dailyAmountLimit");
        requireNonNegative(request.monthlyAmountLimit(), "monthlyAmountLimit");
        requireNonNegative(request.perTransactionLimit(), "perTransactionLimit");
    }

    private void requireNonNegative(BigDecimal value, String field) {
        if (value != null && value.signum() < 0) {
            throw new PaymentGatewayException(field + " must not be negative");
        }
    }

    private void ensureEnabled() {
        if (!featureFlagService.isEnabled(FeatureKeys.PAYOUT_CONTROLS_CONFIG)) {
            throw new PayoutConfigDisabledException(
                    "Payout controls configuration is not enabled (feature flag payout-controls-config is off).");
        }
    }

    private long parseMerchantId(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new PaymentGatewayException("Invalid merchantId filter: " + value);
        }
    }

    private long requireLong(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal nullableAmount(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal cleaned = value.stripTrailingZeros();
        return cleaned.signum() == 0 ? null : cleaned;
    }

    private Integer nullableVelocity(Integer value) {
        if (value == null || value <= 0) {
            return null;
        }
        return value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeOrDefault(String value, String defaultValue) {
        return blank(value) ? defaultValue : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeFlag(String value, String defaultValue) {
        return blank(value) ? defaultValue : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
