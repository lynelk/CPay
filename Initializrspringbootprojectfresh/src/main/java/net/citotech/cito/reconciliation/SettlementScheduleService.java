package net.citotech.cito.reconciliation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementScheduleService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final SettlementOpsService settlementOpsService;

    public SettlementScheduleService(NamedParameterJdbcTemplate jdbcTemplate,
                                     SettlementOpsService settlementOpsService) {
        this.jdbcTemplate = jdbcTemplate;
        this.settlementOpsService = settlementOpsService;
    }

    @Transactional
    public void configure(long merchantId,
                          String providerCode,
                          String channelCode,
                          String currency,
                          BigDecimal minimumRetainedBalance,
                          int sweepHour) {
        if (merchantId <= 0 || blank(providerCode) || blank(channelCode) || blank(currency)) {
            throw new PaymentGatewayException("merchant, provider, channel, and currency are required");
        }
        if (sweepHour < 0 || sweepHour > 23) {
            throw new PaymentGatewayException("sweepHour must be between 0 and 23");
        }
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchant_id", merchantId);
        p.addValue("provider_code", providerCode.trim().toUpperCase());
        p.addValue("channel_code", channelCode.trim());
        p.addValue("currency", currency.trim().toUpperCase());
        p.addValue("minimum_retained_balance", minimumRetainedBalance == null ? BigDecimal.ZERO : minimumRetainedBalance);
        p.addValue("sweep_hour", sweepHour);
        jdbcTemplate.update(
            "INSERT INTO settlement_schedules "
                + "(merchant_id, provider_code, channel_code, currency, minimum_retained_balance, sweep_hour) "
                + "VALUES (:merchant_id, :provider_code, :channel_code, :currency, :minimum_retained_balance, :sweep_hour) "
                + "ON DUPLICATE KEY UPDATE schedule_status='ACTIVE', minimum_retained_balance=:minimum_retained_balance, "
                + "sweep_hour=:sweep_hour",
            p);
    }

    @Transactional
    public List<SettlementSweepResult> runDueSweeps() {
        return runDueSweeps(LocalDate.now(), LocalTime.now().getHour());
    }

    @Transactional
    public List<SettlementSweepResult> runDueSweeps(LocalDate runDate, int sweepHour) {
        List<ScheduleRow> schedules = loadDueSchedules(sweepHour);
        return schedules.stream()
            .map(schedule -> runSweep(schedule, runDate))
            .toList();
    }

    private List<ScheduleRow> loadDueSchedules(int sweepHour) {
        return jdbcTemplate.query(
            "SELECT ss.id, ss.merchant_id, ss.provider_code, ss.channel_code, ss.currency, "
                + "ss.minimum_retained_balance, COALESCE(mcb.available_balance, 0) AS available_balance "
                + "FROM settlement_schedules ss "
                + "LEFT JOIN merchant_channel_balances mcb "
                + "ON mcb.merchant_id=ss.merchant_id AND mcb.channel_code=ss.channel_code AND mcb.currency=ss.currency "
                + "WHERE ss.schedule_status='ACTIVE' AND ss.sweep_hour=:sweep_hour",
            new MapSqlParameterSource("sweep_hour", sweepHour),
            (rs, rowNum) -> new ScheduleRow(
                rs.getLong("id"),
                rs.getLong("merchant_id"),
                rs.getString("provider_code"),
                rs.getString("channel_code"),
                rs.getString("currency"),
                rs.getBigDecimal("minimum_retained_balance"),
                rs.getBigDecimal("available_balance")));
    }

    private SettlementSweepResult runSweep(ScheduleRow schedule, LocalDate runDate) {
        BigDecimal available = schedule.availableBalance == null ? BigDecimal.ZERO : schedule.availableBalance;
        BigDecimal retained = schedule.minimumRetainedBalance == null ? BigDecimal.ZERO : schedule.minimumRetainedBalance;
        BigDecimal sweepAmount = available.subtract(retained);
        String reference = "settlement-" + schedule.id + "-" + runDate;
        if (sweepAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return new SettlementSweepResult(reference, "SKIPPED", BigDecimal.ZERO, "minimum retained balance not reached");
        }
        if (insertRun(schedule.id, reference, "OPENED", sweepAmount, "settlement batch opened") == 0) {
            return new SettlementSweepResult(reference, "ALREADY_RUN", sweepAmount, "settlement sweep already exists");
        }
        settlementOpsService.openBatch(
            reference,
            schedule.providerCode,
            schedule.channelCode,
            schedule.currency,
            sweepAmount,
            "settlement-scheduler");
        return new SettlementSweepResult(reference, "OPENED", sweepAmount, "settlement batch opened");
    }

    private int insertRun(long scheduleId, String reference, String status, BigDecimal amount, String message) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("schedule_id", scheduleId);
        p.addValue("reference", reference);
        p.addValue("status", status);
        p.addValue("amount", amount);
        p.addValue("message", message);
        return jdbcTemplate.update(
            "INSERT IGNORE INTO settlement_sweep_runs "
                + "(schedule_id, run_reference, run_status, sweep_amount, message) "
                + "VALUES (:schedule_id, :reference, :status, :amount, :message)",
            p);
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record ScheduleRow(long id,
                               long merchantId,
                               String providerCode,
                               String channelCode,
                               String currency,
                               BigDecimal minimumRetainedBalance,
                               BigDecimal availableBalance) {
    }
}
