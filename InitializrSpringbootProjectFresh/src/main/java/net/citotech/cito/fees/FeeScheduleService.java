package net.citotech.cito.fees;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.money.MoneyAmount;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Versioned, effective-dated fee schedules with optional per-merchant overrides.
 *
 * <p>Schedules are append-only in commercial effect: a replacement closes the prior effective
 * window instead of deleting history. Unsupported pricing models fail closed rather than silently
 * falling back to a different formula.
 */
@Service
public class FeeScheduleService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public FeeScheduleService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public FeeSchedule create(
            String gatewayId,
            Long merchantId,
            String service,
            String chargeType,
            String chargingMethod,
            BigDecimal amount,
            Instant effectiveFrom,
            String createdBy) {
        requireEnum(service, "PAYIN", "PAYOUT", "service");
        requireEnum(chargeType, "CUSTOMER_CHARGE", "COST_OF_PAYMENT", "chargeType");
        requireEnum(chargingMethod, "PERCENTAGE", "FLAT_FEE", "chargingMethod");
        validateAmount(chargingMethod, amount);
        if (gatewayId == null || gatewayId.isBlank()) {
            throw new PaymentGatewayException("gatewayId is required");
        }
        if (createdBy == null || createdBy.isBlank()) {
            throw new PaymentGatewayException("createdBy is required");
        }

        BigDecimal normalizedAmount = MoneyAmount.normalize(amount);
        Instant startsAt = effectiveFrom == null ? Instant.now() : effectiveFrom;

        MapSqlParameterSource closeParams = new MapSqlParameterSource();
        closeParams.addValue("gateway_id", gatewayId.trim());
        closeParams.addValue("merchant_id", merchantId);
        closeParams.addValue("service", service);
        closeParams.addValue("charge_type", chargeType);
        closeParams.addValue("effective_to", Timestamp.from(startsAt));
        jdbcTemplate.update(
                "UPDATE fee_schedules SET effective_to=:effective_to "
                        + "WHERE gateway_id=:gateway_id AND service=:service AND charge_type=:charge_type "
                        + "AND (merchant_id <=> :merchant_id) AND effective_to IS NULL",
                closeParams);

        MapSqlParameterSource insertParams = new MapSqlParameterSource();
        insertParams.addValue("gateway_id", gatewayId.trim());
        insertParams.addValue("merchant_id", merchantId);
        insertParams.addValue("service", service);
        insertParams.addValue("charge_type", chargeType);
        insertParams.addValue("charging_method", chargingMethod);
        insertParams.addValue("amount", normalizedAmount);
        insertParams.addValue("effective_from", Timestamp.from(startsAt));
        insertParams.addValue("created_by", createdBy.trim());
        jdbcTemplate.update(
                "INSERT INTO fee_schedules "
                        + "(gateway_id, merchant_id, service, charge_type, charging_method, amount, effective_from, created_by) "
                        + "VALUES (:gateway_id, :merchant_id, :service, :charge_type, :charging_method, :amount, :effective_from, :created_by)",
                insertParams);

        return currentSchedule(gatewayId.trim(), merchantId, service, chargeType)
                .orElseThrow(
                        () ->
                                new PaymentGatewayException(
                                        "Failed to read back the fee schedule just created"));
    }

    /** Merchant-specific active schedule wins over the global (merchant_id IS NULL) one. */
    public Optional<FeeSchedule> currentSchedule(
            String gatewayId, Long merchantId, String service, String chargeType) {
        if (merchantId != null) {
            Optional<FeeSchedule> merchantSpecific =
                    activeScheduleFor(gatewayId, merchantId, service, chargeType);
            if (merchantSpecific.isPresent()) {
                return merchantSpecific;
            }
        }
        return activeScheduleFor(gatewayId, null, service, chargeType);
    }

    private Optional<FeeSchedule> activeScheduleFor(
            String gatewayId, Long merchantId, String service, String chargeType) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("gateway_id", gatewayId);
        p.addValue("merchant_id", merchantId);
        p.addValue("service", service);
        p.addValue("charge_type", chargeType);
        List<FeeSchedule> rows =
                jdbcTemplate.query(
                        "SELECT id, gateway_id, merchant_id, service, charge_type, charging_method, amount, effective_from, effective_to "
                                + "FROM fee_schedules "
                                + "WHERE gateway_id=:gateway_id AND (merchant_id <=> :merchant_id) "
                                + "AND service=:service AND charge_type=:charge_type "
                                + "AND effective_from <= CURRENT_TIMESTAMP "
                                + "AND (effective_to IS NULL OR effective_to > CURRENT_TIMESTAMP) "
                                + "ORDER BY effective_from DESC LIMIT 1",
                        p,
                        (rs, rowNum) ->
                                new FeeSchedule(
                                        rs.getLong("id"),
                                        rs.getString("gateway_id"),
                                        rs.getObject("merchant_id") == null
                                                ? null
                                                : rs.getLong("merchant_id"),
                                        rs.getString("service"),
                                        rs.getString("charge_type"),
                                        rs.getString("charging_method"),
                                        rs.getBigDecimal("amount"),
                                        rs.getTimestamp("effective_from").toInstant(),
                                        rs.getTimestamp("effective_to") == null
                                                ? null
                                                : rs.getTimestamp("effective_to").toInstant()));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private void validateAmount(String chargingMethod, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new PaymentGatewayException("amount must be greater than zero");
        }
        if ("PERCENTAGE".equals(chargingMethod) && amount.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new PaymentGatewayException("percentage fee must not exceed 100");
        }
    }

    private void requireEnum(String value, String option1, String option2, String field) {
        requireOneOf(value, field, option1, option2);
    }

    private void requireOneOf(String value, String field, String... allowed) {
        for (String candidate : allowed) {
            if (candidate.equals(value)) {
                return;
            }
        }
        throw new PaymentGatewayException(
                field + " must be one of " + java.util.Arrays.toString(allowed));
    }
}
