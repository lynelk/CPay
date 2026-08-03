package net.citotech.cito.reconciliation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Reconciliation finance close with maker-checker separation. The former single-actor {@link
 * #dailyClose} write flipped the day straight to CLOSED; it is now the maker submit - it computes
 * the matched/unmatched/exception/variance summary, rejects the submission up front when the
 * unmatched variance exceeds the configured tolerance ({@code finance_variance_tolerance_amount}
 * setting, default 0 = no tolerance, fail closed), and writes the row as {@code PENDING_APPROVAL}.
 * A different actor must then {@link #approveDailyClose} (checker) to reach {@code CLOSED}.
 */
@Service
public class FinanceWorkflowService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public FinanceWorkflowService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int postApprovedReview(long reviewId, String postedBy) {
        String sql =
                "UPDATE reconciliation_reviews SET review_status='POSTED', reviewed_by=:posted_by, reviewed_at=CURRENT_TIMESTAMP, review_note='posted to finance workflow' WHERE id=:id AND review_status='APPROVED'";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("id", reviewId);
        p.addValue("posted_by", postedBy);
        return jdbcTemplate.update(sql, p);
    }

    /**
     * Maker submit. Returns the id of the close row (existing row on re-submit). A day already
     * CLOSED is not re-opened - the method throws a {@link PaymentGatewayException} instead.
     */
    public long dailyClose(String date, String currency, String closedBy) {
        return submitDailyClose(date, currency, closedBy);
    }

    public long submitDailyClose(String date, String currency, String requestedBy) {
        LocalDate closeDate = parseDate(date, "date");
        if (requestedBy == null || requestedBy.isBlank()) {
            requestedBy = "system";
        }
        Map<String, Object> summary = report(currency);
        BigDecimal variance = new BigDecimal(String.valueOf(summary.get("unmatchedAmount")));
        BigDecimal tolerance = varianceTolerance();
        if (variance != null
                && variance.compareTo(BigDecimal.ZERO) > 0
                && variance.compareTo(tolerance) > 0) {
            throw new PaymentGatewayException(
                    "Daily close rejected: unmatched variance "
                            + variance.toPlainString()
                            + " exceeds tolerance "
                            + tolerance.toPlainString());
        }
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("close_date", closeDate);
        p.addValue("currency", currency);
        p.addValue("matched_count", summary.get("matchedCount"));
        p.addValue("unmatched_count", summary.get("unmatchedCount"));
        p.addValue("exception_count", summary.get("exceptionCount"));
        p.addValue("variance", variance);
        p.addValue("requested_by", requestedBy.trim());
        p.addValue("approved_variance", tolerance);
        // Upsert that keeps an existing CLOSED row closed (never re-opened) and makes a failed
        // maker-approval attempt re-submitable by allowing the same maker to re-request.
        jdbcTemplate.update(
                "INSERT INTO reconciliation_daily_closes (close_date, currency, close_status, matched_count, unmatched_count, exception_count, variance_amount, requested_by, requested_at, approved_variance_amount) "
                        + "VALUES (:close_date, :currency, 'PENDING_APPROVAL', :matched_count, :unmatched_count, :exception_count, :variance, :requested_by, CURRENT_TIMESTAMP, :approved_variance) "
                        + "ON DUPLICATE KEY UPDATE close_status=CASE WHEN close_status='CLOSED' THEN close_status ELSE 'PENDING_APPROVAL' END, "
                        + "matched_count=VALUES(matched_count), unmatched_count=VALUES(unmatched_count), exception_count=VALUES(exception_count), variance_amount=VALUES(variance_amount), "
                        + "requested_by=CASE WHEN close_status='CLOSED' THEN requested_by ELSE VALUES(requested_by) END, requested_at=CURRENT_TIMESTAMP, "
                        + "approved_variance_amount=VALUES(approved_variance_amount)",
                p);
        String status =
                jdbcTemplate.queryForObject(
                        "SELECT close_status FROM reconciliation_daily_closes WHERE close_date=:close_date AND currency=:currency",
                        p,
                        String.class);
        if ("CLOSED".equals(status)) {
            throw new PaymentGatewayException(
                    "Daily close already closed for " + closeDate + " " + currency);
        }
        return jdbcTemplate.queryForObject(
                "SELECT id FROM reconciliation_daily_closes WHERE close_date=:close_date AND currency=:currency",
                p,
                Long.class);
    }

    /**
     * Checker approval: the approver must differ from the maker and the row must still be
     * PENDING_APPROVAL. Returns the number of rows flipped to CLOSED (0 = same-actor approve, not
     * pending, or missing row).
     */
    public int approveDailyClose(String date, String currency, String approvedBy) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("close_date", parseDate(date, "date"));
        p.addValue("currency", currency);
        p.addValue("approved_by", approvedBy);
        return jdbcTemplate.update(
                "UPDATE reconciliation_daily_closes SET close_status='CLOSED', closed_by=:approved_by, closed_at=CURRENT_TIMESTAMP, "
                        + "approved_by=:approved_by, approved_at=CURRENT_TIMESTAMP, rejection_reason=NULL "
                        + "WHERE close_date=:close_date AND currency=:currency AND close_status='PENDING_APPROVAL' "
                        + "AND requested_by IS NOT NULL AND requested_by<>:approved_by",
                p);
    }

    /**
     * Checker rejection: records the reason and keeps the row PENDING_APPROVAL so the maker can
     * correct and re-submit. Returns rows affected (0 = same-actor reject or not pending).
     */
    public int rejectDailyClose(String date, String currency, String rejectedBy, String reason) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("close_date", parseDate(date, "date"));
        p.addValue("currency", currency);
        p.addValue("rejected_by", rejectedBy);
        p.addValue(
                "reason",
                reason == null || reason.isBlank() ? "Rejected by checker" : reason.trim());
        return jdbcTemplate.update(
                "UPDATE reconciliation_daily_closes SET rejection_reason=:reason "
                        + "WHERE close_date=:close_date AND currency=:currency AND close_status='PENDING_APPROVAL' "
                        + "AND requested_by IS NOT NULL AND requested_by<>:rejected_by",
                p);
    }

    public Map<String, Object> report(String currency) {
        Map<String, Object> result = new HashMap<>();
        result.put("matchedCount", count(currency, "MATCHED"));
        result.put("manualMatchCount", count(currency, "MANUAL_MATCH"));
        result.put("unmatchedCount", count(currency, "UNMATCHED"));
        result.put("exceptionCount", exceptionCount(currency));
        result.put("unmatchedAmount", amount(currency, "UNMATCHED"));
        return result;
    }

    /**
     * Variance tolerance from the global settings table. Unset or non-numeric defaults to ZERO -
     * fail closed: any unmatched variance blocks the close submit until finance configures a
     * tolerance.
     */
    private BigDecimal varianceTolerance() {
        String configured = settingValue("finance_variance_tolerance_amount");
        if (configured == null || configured.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(configured.trim());
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private String settingValue(String key) {
        try {
            java.util.List<String> rows =
                    jdbcTemplate.query(
                            "SELECT setting_value FROM settings WHERE name=:name LIMIT 1",
                            new MapSqlParameterSource("name", key),
                            (rs, rowNum) -> rs.getString("setting_value"));
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate parseDate(String value, String field) {
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            throw new PaymentGatewayException("Invalid " + field + ": " + value);
        }
    }

    private Integer count(String currency, String status) {
        String sql =
                "SELECT COUNT(*) FROM reconciliation_records WHERE currency=:currency AND match_status=:status";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("currency", currency);
        p.addValue("status", status);
        return jdbcTemplate.queryForObject(sql, p, Integer.class);
    }

    private Integer exceptionCount(String currency) {
        String sql =
                "SELECT COUNT(*) FROM reconciliation_records WHERE currency=:currency AND exception_category IS NOT NULL";
        return jdbcTemplate.queryForObject(
                sql, new MapSqlParameterSource("currency", currency), Integer.class);
    }

    private BigDecimal amount(String currency, String status) {
        String sql =
                "SELECT COALESCE(SUM(amount),0) FROM reconciliation_records WHERE currency=:currency AND match_status=:status";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("currency", currency);
        p.addValue("status", status);
        return jdbcTemplate.queryForObject(sql, p, BigDecimal.class);
    }
}
