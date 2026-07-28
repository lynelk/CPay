package net.citotech.cito.reporting;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.citotech.cito.ErrorCatalog;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Read-only queries over the reporting aggregate tables populated nightly by
 * {@code net.citotech.cito.scheduler.ReportingAggregateService} (audit F4/N10/O3), used by
 * {@link ReportingAdminController}. These read pre-aggregated rows only - never
 * {@code merchant_transactions_log} directly.
 */
@Service
public class ReportingQueryService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ReportingQueryService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Audit F4: daily transaction stats for a date range, optionally filtered by merchant/gateway. */
    public List<Map<String, Object>> transactionStats(LocalDate from, LocalDate to, Long merchantId, String gatewayId) {
        StringBuilder sql = new StringBuilder(
                "SELECT stat_date, merchant_id, gateway_id, tx_type, status, tx_count, total_amount, total_charges "
                        + "FROM daily_transaction_stats WHERE stat_date BETWEEN :from AND :to");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("from", Date.valueOf(from))
                .addValue("to", Date.valueOf(to));
        if (merchantId != null) {
            sql.append(" AND merchant_id = :merchantId");
            params.addValue("merchantId", merchantId);
        }
        if (gatewayId != null && !gatewayId.isBlank()) {
            sql.append(" AND gateway_id = :gatewayId");
            params.addValue("gatewayId", gatewayId);
        }
        sql.append(" ORDER BY stat_date DESC, merchant_id, gateway_id, tx_type, status");

        return jdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("statDate", rs.getDate("stat_date").toLocalDate());
            row.put("merchantId", rs.getLong("merchant_id"));
            row.put("gatewayId", rs.getString("gateway_id"));
            row.put("txType", rs.getString("tx_type"));
            row.put("status", rs.getString("status"));
            row.put("count", rs.getInt("tx_count"));
            row.put("totalAmount", rs.getBigDecimal("total_amount"));
            row.put("totalCharges", rs.getBigDecimal("total_charges"));
            return row;
        });
    }

    /**
     * Audit N10: daily failure-reason stats for a date range, each row annotated with
     * {@link ErrorCatalog} category/retryable/docs metadata whenever a structured
     * {@code error_code} was extracted for that row.
     */
    public List<Map<String, Object>> failureReasonStats(LocalDate from, LocalDate to, String gatewayId) {
        StringBuilder sql = new StringBuilder(
                "SELECT stat_date, gateway_id, error_code, failure_reason, tx_count "
                        + "FROM daily_failure_reason_stats WHERE stat_date BETWEEN :from AND :to");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("from", Date.valueOf(from))
                .addValue("to", Date.valueOf(to));
        if (gatewayId != null && !gatewayId.isBlank()) {
            sql.append(" AND gateway_id = :gatewayId");
            params.addValue("gatewayId", gatewayId);
        }
        sql.append(" ORDER BY stat_date DESC, tx_count DESC");

        return jdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("statDate", rs.getDate("stat_date").toLocalDate());
            row.put("gatewayId", rs.getString("gateway_id"));
            String errorCode = rs.getString("error_code");
            row.put("errorCode", errorCode);
            row.put("failureReason", rs.getString("failure_reason"));
            row.put("count", rs.getInt("tx_count"));
            if (errorCode != null && !errorCode.isBlank()) {
                ErrorCatalog.Entry entry = ErrorCatalog.lookup(errorCode);
                row.put("stableErrorCode", entry.stableCode());
                row.put("errorCategory", entry.categoryName());
                row.put("retryable", entry.retryable());
                row.put("docsUrl", entry.docsUrl());
            }
            return row;
        });
    }

    /** Audit O3: the distinct float/stock gateway accounts that have at least one snapshot. */
    public List<String> distinctFloatAccountTypes() {
        return jdbcTemplate.query(
                "SELECT DISTINCT account_type FROM float_balance_snapshots ORDER BY account_type",
                new MapSqlParameterSource(),
                (rs, rowNum) -> rs.getString("account_type"));
    }

    /** Audit O3: balance snapshots for one float account, oldest-first, over a trailing window. */
    public List<BalanceSnapshotPoint> floatBalanceSnapshots(String accountType, int windowDays) {
        LocalDate cutoff = LocalDate.now().minusDays(Math.max(windowDays, 1));
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("accountType", accountType)
                .addValue("cutoff", Date.valueOf(cutoff));
        return jdbcTemplate.query(
                "SELECT stat_date, balance FROM float_balance_snapshots "
                        + "WHERE account_type = :accountType AND stat_date >= :cutoff "
                        + "ORDER BY stat_date ASC",
                params,
                (rs, rowNum) -> new BalanceSnapshotPoint(rs.getDate("stat_date").toLocalDate(), rs.getBigDecimal("balance")));
    }

    /** Audit O3: the most recent float/stock top-up events, newest first. */
    public List<Map<String, Object>> recentTopups(int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", limit);
        return jdbcTemplate.query(
                "SELECT topup_date, account, amount, recorded_by, note FROM float_topups "
                        + "ORDER BY topup_date DESC, id DESC LIMIT :limit",
                params,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("date", rs.getDate("topup_date").toLocalDate());
                    row.put("account", rs.getString("account"));
                    row.put("amount", rs.getBigDecimal("amount"));
                    row.put("recordedBy", rs.getString("recorded_by"));
                    row.put("note", rs.getString("note"));
                    return row;
                });
    }

    /** Records a float/stock top-up event (audit O3), so the topup log has a way to grow. */
    public int recordTopup(String account, BigDecimal amount, String recordedBy, String note) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("topupDate", Date.valueOf(LocalDate.now()))
                .addValue("account", account)
                .addValue("amount", amount)
                .addValue("recordedBy", recordedBy == null || recordedBy.isBlank() ? "system" : recordedBy)
                .addValue("note", note);
        return jdbcTemplate.update(
                "INSERT INTO float_topups (topup_date, account, amount, recorded_by, note) "
                        + "VALUES (:topupDate, :account, :amount, :recordedBy, :note)",
                params);
    }
}
