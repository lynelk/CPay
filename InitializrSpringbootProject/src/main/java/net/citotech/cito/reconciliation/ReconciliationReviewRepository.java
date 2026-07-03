package net.citotech.cito.reconciliation;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReconciliationReviewRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ReconciliationReviewRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void request(long recordId, String transactionId, String type, BigDecimal amount, String currency, String reason, String requestedBy) {
        String sql = "INSERT INTO reconciliation_reviews (reconciliation_record_id, transaction_id, review_type, amount, currency, reason, requested_by) VALUES (:record_id, :transaction_id, :review_type, :amount, :currency, :reason, :requested_by)";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("record_id", recordId);
        p.addValue("transaction_id", transactionId);
        p.addValue("review_type", type);
        p.addValue("amount", amount);
        p.addValue("currency", currency);
        p.addValue("reason", reason);
        p.addValue("requested_by", requestedBy);
        jdbcTemplate.update(sql, p);
    }

    public List<ReconciliationReview> pending(int limit) {
        String sql = "SELECT * FROM reconciliation_reviews WHERE review_status='PENDING' ORDER BY id ASC LIMIT :limit";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("limit", limit), (rs, rowNum) -> {
            ReconciliationReview r = new ReconciliationReview();
            r.id = rs.getLong("id");
            r.reconciliationRecordId = rs.getLong("reconciliation_record_id");
            r.transactionId = rs.getString("transaction_id");
            r.reviewType = rs.getString("review_type");
            r.amount = rs.getBigDecimal("amount");
            r.currency = rs.getString("currency");
            r.reason = rs.getString("reason");
            r.reviewStatus = rs.getString("review_status");
            r.requestedBy = rs.getString("requested_by");
            r.reviewedBy = rs.getString("reviewed_by");
            r.reviewNote = rs.getString("review_note");
            return r;
        });
    }

    public void decide(long id, String status, String reviewedBy, String note) {
        String sql = "UPDATE reconciliation_reviews SET review_status=:status, reviewed_by=:reviewed_by, reviewed_at=CURRENT_TIMESTAMP, review_note=:note WHERE id=:id AND review_status='PENDING'";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("id", id);
        p.addValue("status", status);
        p.addValue("reviewed_by", reviewedBy);
        p.addValue("note", note);
        jdbcTemplate.update(sql, p);
    }
}
