package net.citotech.cito.callback;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CallbackTaskRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CallbackTaskRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void enqueue(long merchantId, String transactionId, String referenceValue, String targetUrl, String requestBody) {
        if (exists(merchantId, transactionId, referenceValue)) return;
        String sql = "INSERT INTO callback_tasks (merchant_id, transaction_id, reference_value, target_url, request_body, task_status, attempt_count, attempt_limit, next_run_at) VALUES (:merchant_id, :transaction_id, :reference_value, :target_url, :request_body, 'PENDING', 0, 5, CURRENT_TIMESTAMP)";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchant_id", merchantId);
        p.addValue("transaction_id", transactionId);
        p.addValue("reference_value", referenceValue);
        p.addValue("target_url", targetUrl);
        p.addValue("request_body", requestBody);
        jdbcTemplate.update(sql, p);
    }

    private boolean exists(long merchantId, String transactionId, String referenceValue) {
        String sql = "SELECT COUNT(*) FROM callback_tasks WHERE merchant_id=:merchant_id AND transaction_id=:transaction_id AND reference_value=:reference_value";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchant_id", merchantId);
        p.addValue("transaction_id", transactionId);
        p.addValue("reference_value", referenceValue);
        Integer count = jdbcTemplate.queryForObject(sql, p, Integer.class);
        return count != null && count > 0;
    }

    public List<CallbackTask> findDue(int limit) {
        String sql = "SELECT * FROM callback_tasks WHERE task_status IN ('PENDING','RETRY') AND (next_run_at IS NULL OR next_run_at <= CURRENT_TIMESTAMP) ORDER BY id ASC LIMIT :limit";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("limit", limit), (rs, rowNum) -> map(rs));
    }

    public CallbackTask findById(long id) {
        List<CallbackTask> rows = jdbcTemplate.query("SELECT * FROM callback_tasks WHERE id=:id", new MapSqlParameterSource("id", id), (rs, rowNum) -> map(rs));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void markDone(long id) {
        jdbcTemplate.update("UPDATE callback_tasks SET task_status='DONE', attempt_count=attempt_count+1, last_run_at=CURRENT_TIMESTAMP, message=NULL WHERE id=:id", new MapSqlParameterSource("id", id));
    }

    public void markNext(long id, int attemptCount, int attemptLimit, Instant nextRunAt, String message) {
        String status = attemptCount + 1 >= attemptLimit ? "PARKED" : "RETRY";
        String sql = "UPDATE callback_tasks SET task_status=:status, attempt_count=attempt_count+1, last_run_at=CURRENT_TIMESTAMP, next_run_at=:next_run_at, message=:message WHERE id=:id";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("id", id);
        p.addValue("status", status);
        p.addValue("next_run_at", Timestamp.from(nextRunAt));
        p.addValue("message", message);
        jdbcTemplate.update(sql, p);
    }

    private CallbackTask map(java.sql.ResultSet rs) throws java.sql.SQLException {
        CallbackTask t = new CallbackTask();
        t.id = rs.getLong("id");
        t.merchantId = rs.getLong("merchant_id");
        t.transactionId = rs.getString("transaction_id");
        t.referenceValue = rs.getString("reference_value");
        t.targetUrl = rs.getString("target_url");
        t.requestBody = rs.getString("request_body");
        t.taskStatus = rs.getString("task_status");
        t.attemptCount = rs.getInt("attempt_count");
        t.attemptLimit = rs.getInt("attempt_limit");
        t.message = rs.getString("message");
        return t;
    }
}
