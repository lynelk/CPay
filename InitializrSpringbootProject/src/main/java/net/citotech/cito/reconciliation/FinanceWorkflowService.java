package net.citotech.cito.reconciliation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class FinanceWorkflowService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public FinanceWorkflowService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int postApprovedReview(long reviewId, String postedBy) {
        String sql = "UPDATE reconciliation_reviews SET review_status='POSTED', reviewed_by=:posted_by, reviewed_at=CURRENT_TIMESTAMP, review_note='posted to finance workflow' WHERE id=:id AND review_status='APPROVED'";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("id", reviewId);
        p.addValue("posted_by", postedBy);
        return jdbcTemplate.update(sql, p);
    }

    public long dailyClose(String date, String currency, String closedBy) {
        LocalDate closeDate = LocalDate.parse(date);
        Map<String, Object> summary = report(currency);
        BigDecimal variance = new BigDecimal(String.valueOf(summary.get("unmatchedAmount")));
        String sql = "INSERT INTO reconciliation_daily_closes (close_date, currency, close_status, matched_count, unmatched_count, exception_count, variance_amount, closed_by, closed_at) VALUES (:close_date, :currency, 'CLOSED', :matched_count, :unmatched_count, :exception_count, :variance, :closed_by, CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE close_status='CLOSED', matched_count=:matched_count, unmatched_count=:unmatched_count, exception_count=:exception_count, variance_amount=:variance, closed_by=:closed_by, closed_at=CURRENT_TIMESTAMP";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("close_date", closeDate);
        p.addValue("currency", currency);
        p.addValue("matched_count", summary.get("matchedCount"));
        p.addValue("unmatched_count", summary.get("unmatchedCount"));
        p.addValue("exception_count", summary.get("exceptionCount"));
        p.addValue("variance", variance);
        p.addValue("closed_by", closedBy);
        jdbcTemplate.update(sql, p);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", new MapSqlParameterSource(), Long.class);
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

    private Integer count(String currency, String status) {
        String sql = "SELECT COUNT(*) FROM reconciliation_records WHERE currency=:currency AND match_status=:status";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("currency", currency);
        p.addValue("status", status);
        return jdbcTemplate.queryForObject(sql, p, Integer.class);
    }

    private Integer exceptionCount(String currency) {
        String sql = "SELECT COUNT(*) FROM reconciliation_records WHERE currency=:currency AND exception_category IS NOT NULL";
        return jdbcTemplate.queryForObject(sql, new MapSqlParameterSource("currency", currency), Integer.class);
    }

    private BigDecimal amount(String currency, String status) {
        String sql = "SELECT COALESCE(SUM(amount),0) FROM reconciliation_records WHERE currency=:currency AND match_status=:status";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("currency", currency);
        p.addValue("status", status);
        return jdbcTemplate.queryForObject(sql, p, BigDecimal.class);
    }
}
