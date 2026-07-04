package net.citotech.cito.callback;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CallbackClaimRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final String workerName = "worker-" + UUID.randomUUID();

    public CallbackClaimRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Long> claimDueIds(int limit) {
        String selectSql = "SELECT id FROM callback_tasks WHERE task_status IN ('PENDING','RETRY') AND (next_run_at IS NULL OR next_run_at <= CURRENT_TIMESTAMP) ORDER BY id ASC LIMIT :limit";
        List<Long> ids = jdbcTemplate.query(selectSql, new MapSqlParameterSource("limit", limit), (rs, rowNum) -> rs.getLong("id"));
        for (Long id : ids) tryClaim(id);
        String claimedSql = "SELECT task_id FROM callback_task_claims WHERE worker_name=:worker_name AND claim_status='ACTIVE' ORDER BY id ASC LIMIT :limit";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("worker_name", workerName);
        p.addValue("limit", limit);
        return jdbcTemplate.query(claimedSql, p, (rs, rowNum) -> rs.getLong("task_id"));
    }

    public void release(long taskId) {
        String sql = "DELETE FROM callback_task_claims WHERE task_id=:task_id AND worker_name=:worker_name AND claim_status='ACTIVE'";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("task_id", taskId);
        p.addValue("worker_name", workerName);
        jdbcTemplate.update(sql, p);
    }

    private void tryClaim(Long taskId) {
        try {
            String sql = "INSERT INTO callback_task_claims (task_id, worker_name, claim_status) VALUES (:task_id, :worker_name, 'ACTIVE')";
            MapSqlParameterSource p = new MapSqlParameterSource();
            p.addValue("task_id", taskId);
            p.addValue("worker_name", workerName);
            jdbcTemplate.update(sql, p);
        } catch (Exception ignored) {
            // Another worker already claimed it.
        }
    }
}
