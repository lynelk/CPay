package net.citotech.cito.callback;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CallbackAdminService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final CallbackSecretService secretService;

    public CallbackAdminService(NamedParameterJdbcTemplate jdbcTemplate, CallbackSecretService secretService) {
        this.jdbcTemplate = jdbcTemplate;
        this.secretService = secretService;
    }

    public String rotateSecret(long merchantId, String alias) {
        return secretService.rotate(merchantId, alias);
    }

    public int requeueParked(long taskId) {
        String sql = "UPDATE callback_tasks SET task_status='RETRY', next_run_at=CURRENT_TIMESTAMP, message='manually requeued' WHERE id=:id AND task_status='PARKED'";
        return jdbcTemplate.update(sql, new MapSqlParameterSource("id", taskId));
    }

    public int requeueMerchant(long merchantId) {
        String sql = "UPDATE callback_tasks SET task_status='RETRY', next_run_at=CURRENT_TIMESTAMP, message='merchant callbacks manually requeued' WHERE merchant_id=:merchant_id AND task_status='PARKED'";
        return jdbcTemplate.update(sql, new MapSqlParameterSource("merchant_id", merchantId));
    }
}

