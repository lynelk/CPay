package net.citotech.cito.scheduler;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.citotech.cito.Common;
import net.citotech.cito.callback.CallbackTaskService;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class CallbackRetryScheduler {
    private static final Logger logger = Logger.getLogger(CallbackRetryScheduler.class.getName());
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final CallbackTaskService taskService;

    public CallbackRetryScheduler(NamedParameterJdbcTemplate jdbcTemplate, CallbackTaskService taskService) {
        this.jdbcTemplate = jdbcTemplate;
        this.taskService = taskService;
    }

    @Scheduled(fixedDelay = 60000)
    public void run() {
        try {
            enqueueFinalRows();
            taskService.processDue(50);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Callback scheduler error: " + e.getMessage(), e);
        }
    }

    private void enqueueFinalRows() {
        String sql = "SELECT id, merchant_id, original_amount, tx_merchant_ref, status, tx_merchant_description, tx_gateway_ref, updated_on, created_on, callback_url, currency FROM "
                + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG
                + " WHERE callback_url IS NOT NULL AND callback_url != '' AND status IN ('SUCCESSFUL','FAILED') AND (callback_status IS NULL OR callback_status='PENDING' OR callback_status='RETRY') LIMIT 50";
        List<Object[]> rows = jdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, rowNum) -> new Object[]{
                rs.getLong("id"), rs.getLong("merchant_id"), rs.getBigDecimal("original_amount"), rs.getString("tx_merchant_ref"),
                rs.getString("status"), rs.getString("tx_merchant_description"), rs.getString("tx_gateway_ref"), rs.getString("updated_on"),
                rs.getString("created_on"), rs.getString("callback_url"), rs.getString("currency")
        });
        for (Object[] row : rows) enqueue(row);
    }

    private void enqueue(Object[] row) {
        try {
            long txId = (long) row[0];
            JSONObject body = new JSONObject();
            body.put("amount", row[2] == null ? "0.00" : row[2].toString());
            body.put("reference", row[3]);
            body.put("status", row[4]);
            body.put("description", row[5]);
            body.put("network_ref", row[6]);
            body.put("completed_on", row[7]);
            body.put("created_on", row[8]);
            body.put("currency", row[10] == null ? "" : row[10]);
            taskService.enqueue((long) row[1], String.valueOf(txId), String.valueOf(row[3]), String.valueOf(row[9]), body.toString());
            jdbcTemplate.update("UPDATE " + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG + " SET callback_status='QUEUED' WHERE id=:id", new MapSqlParameterSource("id", txId));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Queue enqueue failed: " + e.getMessage(), e);
        }
    }
}
