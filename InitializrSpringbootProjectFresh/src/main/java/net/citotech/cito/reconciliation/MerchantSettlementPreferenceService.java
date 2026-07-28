package net.citotech.cito.reconciliation;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Merchant self-service settlement scheduling preference (audit N4). Today
 * {@link SettlementScheduleService} sweeps every merchant purely on the ops-configured
 * {@code settlement_schedules} row (provider/channel/currency + a fixed sweep hour) with no notion
 * of daily vs weekly cadence or a minimum amount worth bothering to settle. This service stores one
 * override row per merchant, upserted via {@code ON DUPLICATE KEY UPDATE} exactly like
 * {@code MerchantEnvironmentService}, that {@link SettlementScheduleService} consults - in addition
 * to, not instead of - the existing ops schedule before opening a settlement batch.
 */
@Service
public class MerchantSettlementPreferenceService {
    public static final String DAILY = "DAILY";
    public static final String WEEKLY = "WEEKLY";
    private static final String DEFAULT_WEEKLY_DAY = "MONDAY";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public MerchantSettlementPreferenceService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<MerchantSettlementPreference> find(long merchantId) {
        List<MerchantSettlementPreference> rows = jdbcTemplate.query(
            "SELECT id, merchant_id, settlement_frequency, settlement_day_of_week, "
                + "minimum_settlement_amount, updated_by, created_at, updated_at "
                + "FROM merchant_settlement_preferences WHERE merchant_id=:merchant_id",
            new MapSqlParameterSource("merchant_id", merchantId),
            (rs, rowNum) -> new MerchantSettlementPreference(
                rs.getLong("id"),
                rs.getLong("merchant_id"),
                rs.getString("settlement_frequency"),
                rs.getString("settlement_day_of_week"),
                rs.getBigDecimal("minimum_settlement_amount"),
                rs.getString("updated_by"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Always returns a usable preference so callers (the self-service GET endpoint, the scheduler)
     * never need a null check: a merchant who has never saved one defaults to DAILY with no minimum,
     * which matches today's behavior of settling every due run.
     */
    public MerchantSettlementPreference getOrDefault(long merchantId) {
        return find(merchantId).orElseGet(
            () -> new MerchantSettlementPreference(0, merchantId, DAILY, null, BigDecimal.ZERO, null, null, null));
    }

    @Transactional
    public MerchantSettlementPreference save(long merchantId, String settlementFrequency, String settlementDayOfWeek,
            BigDecimal minimumSettlementAmount, String updatedBy) {
        if (merchantId <= 0) {
            throw new PaymentGatewayException("merchantId is required");
        }
        String frequency = requireOneOf(settlementFrequency, "settlementFrequency", DAILY, WEEKLY);
        String dayOfWeek = normalizeDayOfWeek(frequency, settlementDayOfWeek);
        BigDecimal minimumAmount = minimumSettlementAmount == null ? BigDecimal.ZERO : minimumSettlementAmount;
        if (minimumAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new PaymentGatewayException("minimumSettlementAmount cannot be negative");
        }

        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchant_id", merchantId);
        p.addValue("settlement_frequency", frequency);
        p.addValue("settlement_day_of_week", dayOfWeek);
        p.addValue("minimum_settlement_amount", minimumAmount);
        p.addValue("updated_by", updatedBy);
        jdbcTemplate.update(
            "INSERT INTO merchant_settlement_preferences "
                + "(merchant_id, settlement_frequency, settlement_day_of_week, minimum_settlement_amount, updated_by) "
                + "VALUES (:merchant_id, :settlement_frequency, :settlement_day_of_week, :minimum_settlement_amount, :updated_by) "
                + "ON DUPLICATE KEY UPDATE settlement_frequency=:settlement_frequency, "
                + "settlement_day_of_week=:settlement_day_of_week, minimum_settlement_amount=:minimum_settlement_amount, "
                + "updated_by=:updated_by, updated_at=CURRENT_TIMESTAMP",
            p);
        return find(merchantId)
            .orElseThrow(() -> new PaymentGatewayException("Failed to read back the settlement preference just saved"));
    }

    /**
     * Whether a WEEKLY preference's chosen day matches {@code runDate}. DAILY, and merchants with no
     * saved preference at all, are due on every run - preserving today's behavior unless a merchant
     * has explicitly opted into a slower cadence.
     */
    public boolean isDueOn(MerchantSettlementPreference preference, LocalDate runDate) {
        if (preference == null || !WEEKLY.equals(preference.settlementFrequency())) {
            return true;
        }
        String day = preference.settlementDayOfWeek() == null ? DEFAULT_WEEKLY_DAY : preference.settlementDayOfWeek();
        return runDate.getDayOfWeek().name().equals(day);
    }

    private String normalizeDayOfWeek(String frequency, String dayOfWeek) {
        if (!WEEKLY.equals(frequency)) {
            return null;
        }
        if (dayOfWeek == null || dayOfWeek.trim().isEmpty()) {
            return DEFAULT_WEEKLY_DAY;
        }
        String normalized = dayOfWeek.trim().toUpperCase();
        try {
            DayOfWeek.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new PaymentGatewayException("settlementDayOfWeek must be a valid day of week (e.g. MONDAY)");
        }
        return normalized;
    }

    private String requireOneOf(String value, String field, String... allowed) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        for (String candidate : allowed) {
            if (candidate.equals(normalized)) {
                return normalized;
            }
        }
        throw new PaymentGatewayException(field + " must be one of " + Arrays.toString(allowed));
    }
}
