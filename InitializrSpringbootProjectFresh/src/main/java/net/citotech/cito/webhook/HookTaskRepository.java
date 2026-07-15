package net.citotech.cito.webhook;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class HookTaskRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public HookTaskRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void enqueue(long merchantId, String transactionId, String reference, String callbackUrl, String body) {
        String sql = "INSERT INTO webhook_deliveries "
                + "(merchant_id, transaction_id, merchant_reference, callback_url, payload, status, attempts, max_attempts, next_retry_at) "
                + "VALUES (:merchant_id, :transaction_id, :merchant_reference, :callback_url, :payload, 'PENDING', 0, 5, CURRENT_TIMESTAMP)";
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("merchant_id", merchantId);
        parameters.addValue("transaction_id", transactionId);
        parameters.addValue("merchant_reference", reference);
        parameters.addValue("callback_url", callbackUrl);
        parameters.addValue("payload", body);
        jdbcTemplate.update(sql, parameters);
    }

    public List<HookTask> findDue(int limit) {
        String sql = "SELECT * FROM webhook_deliveries "
                + "WHERE status IN ('PENDING','RETRY') AND (next_retry_at IS NULL OR next_retry_at <= CURRENT_TIMESTAMP) "
                + "ORDER BY id ASC LIMIT :limit";
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("limit", limit);
        return jdbcTemplate.query(sql, parameters, (rs, rowNum) -> {
            HookTask task = new HookTask();
            task.setId(rs.getLong("id"));
            task.setMerchantId(rs.getLong("merchant_id"));
            task.setTransactionId(rs.getString("transaction_id"));
            task.setReference(rs.getString("merchant_reference"));
            task.setCallbackUrl(rs.getString("callback_url"));
            task.setBody(rs.getString("payload"));
            task.setStatus(rs.getString("status"));
            task.setAttempts(rs.getInt("attempts"));
            task.setMaxAttempts(rs.getInt("max_attempts"));
            task.setLastError(rs.getString("last_error"));
            return task;
        });
    }

    public void markDelivered(long id) {
        String sql = "UPDATE webhook_deliveries SET status='DELIVERED', attempts=attempts+1, last_attempt_at=CURRENT_TIMESTAMP, last_error=NULL WHERE id=:id";
        jdbcTemplate.update(sql, new MapSqlParameterSource("id", id));
    }

    public void markRetry(long id, int attempts, int maxAttempts, Instant nextRetryAt, String error) {
        String status = attempts + 1 >= maxAttempts ? "DEAD_LETTER" : "RETRY";
        String sql = "UPDATE webhook_deliveries SET status=:status, attempts=attempts+1, last_attempt_at=CURRENT_TIMESTAMP, "
                + "next_retry_at=:next_retry_at, last_error=:last_error WHERE id=:id";
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("id", id);
        parameters.addValue("status", status);
        parameters.addValue("next_retry_at", Timestamp.from(nextRetryAt));
        parameters.addValue("last_error", error);
        jdbcTemplate.update(sql, parameters);
    }
}

