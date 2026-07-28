package net.citotech.cito.scheduler;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.citotech.cito.Common;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.Model.Transaction;
import net.citotech.cito.callback.CallbackTaskService;
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
        String sql = "SELECT * FROM "
                + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG
                + " WHERE callback_url IS NOT NULL AND callback_url != '' AND status IN ('SUCCESSFUL','FAILED') AND (callback_status IS NULL OR callback_status='PENDING' OR callback_status='RETRY') LIMIT 50";
        List<Transaction> rows = jdbcTemplate.query(sql, new MapSqlParameterSource(), Common.getTransactionRowMapper());
        for (Transaction row : rows) enqueue(row);
    }

    private void enqueue(Transaction tx) {
        try {
            Merchant merchant = Common.getMerchantById(tx.getMerchant_id(), jdbcTemplate);
            Common.enqueueMerchantCallback(tx, merchant, jdbcTemplate);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Queue enqueue failed: " + e.getMessage(), e);
        }
    }
}

