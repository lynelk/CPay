package net.citotech.cito.scheduler;

import net.citotech.cito.Common;
import net.citotech.cito.Model.HttpRequestResponse;
import net.citotech.cito.Model.Merchant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Scheduler that retries failed merchant callbacks with exponential backoff.
 * Retry schedule: 1 min, 5 min, 30 min (up to 3 retries).
 */
@Component
@EnableScheduling
public class CallbackRetryScheduler {

    private static final Logger logger = Logger.getLogger(CallbackRetryScheduler.class.getName());
    private static final int MAX_RETRIES = 3;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * Runs every minute to retry pending callbacks.
     */
    @Scheduled(fixedDelay = 60000)
    public void retryCallbacks() {
        try {
            String sql = "SELECT t.*, m.private_key, m.account_number "
                    + "FROM " + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG + " t "
                    + "JOIN " + Common.DB_TABLE_MERCHANTS + " m ON t.merchant_id = m.id "
                    + "WHERE t.callback_url IS NOT NULL AND t.callback_url != '' "
                    + "AND (t.status = 'SUCCESSFUL' OR t.status = 'FAILED') "
                    + "AND (t.callback_status = 'PENDING' OR t.callback_status = 'RETRY') "
                    + "AND (t.callback_next_retry IS NULL OR t.callback_next_retry <= NOW()) "
                    + "AND t.callback_retry_count < :max_retries "
                    + "LIMIT 50";

            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("max_retries", MAX_RETRIES);

            RowMapper<Object[]> rm = (rs, rowNum) -> new Object[]{
                rs.getLong("id"),
                rs.getString("payer_number"),
                rs.getDouble("original_amount"),
                rs.getString("created_on"),
                rs.getString("tx_merchant_ref"),
                rs.getString("status"),
                rs.getString("tx_merchant_description"),
                rs.getString("tx_gateway_ref"),
                rs.getString("updated_on"),
                rs.getString("callback_url"),
                rs.getString("private_key"),
                rs.getInt("callback_retry_count")
            };

            List<Object[]> rows = jdbcTemplate.query(sql, params, rm);
            for (Object[] row : rows) {
                retryCallback(row);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "CallbackRetryScheduler error: " + e.getMessage(), e);
        }
    }

    private void retryCallback(Object[] row) {
        long txId = (long) row[0];
        String payerNumber = (String) row[1];
        double amount = (double) row[2];
        String createdOn = (String) row[3];
        String txMerchantRef = (String) row[4];
        String status = (String) row[5];
        String description = (String) row[6];
        String networkRef = (String) row[7];
        String updatedOn = (String) row[8];
        String callbackUrl = (String) row[9];
        String privateKey = (String) row[10];
        int retryCount = (int) row[11];

        try {
            String amountToSign = amount + "";
            String signedData = payerNumber + amountToSign + createdOn + txMerchantRef + status + description + networkRef;

            if (privateKey == null || privateKey.isEmpty()) {
                markCallbackFailed(txId, retryCount);
                return;
            }

            Signature sign = Signature.getInstance("SHA256withRSA");
            String base64_private_key = privateKey
                    .replace("-----BEGIN PRIVATE KEY-----\n", "")
                    .replace("\n-----END PRIVATE KEY-----\n", "");
            PrivateKey pk = Common.getPrivateKeyFromBase64String(base64_private_key);
            sign.initSign(pk);
            sign.update(signedData.getBytes());
            byte[] digitalSignature = sign.sign();

            JSONObject jObject = new JSONObject();
            jObject.put("amount", amountToSign);
            jObject.put("payer_number", payerNumber);
            jObject.put("reference", txMerchantRef);
            jObject.put("network_ref", networkRef);
            jObject.put("status", status);
            jObject.put("description", description);
            jObject.put("completed_on", updatedOn);
            jObject.put("created_on", createdOn);
            jObject.put("SignedData", signedData);
            jObject.put("signature", Base64.getEncoder().encodeToString(digitalSignature));

            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");

            HttpRequestResponse rs = Common.doHttpRequest("POST", callbackUrl, jObject.toString(), headers);

            if (rs != null && rs.getStatusCode() >= 200 && rs.getStatusCode() < 300) {
                markCallbackSuccess(txId, rs.toString());
            } else {
                markCallbackRetry(txId, retryCount);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Callback retry failed for tx " + txId + ": " + e.getMessage(), e);
            markCallbackRetry(txId, retryCount);
        }
    }

    private void markCallbackSuccess(long txId, String trace) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.execute((TransactionStatus status) -> {
            try {
                String sql = "UPDATE " + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG
                        + " SET callback_status='SUCCESS', callback_trace=:trace"
                        + " WHERE id=:id";
                MapSqlParameterSource p = new MapSqlParameterSource();
                p.addValue("id", txId);
                p.addValue("trace", trace);
                jdbcTemplate.update(sql, p);
            } catch (Exception e) {
                status.setRollbackOnly();
            }
            return null;
        });
    }

    private void markCallbackRetry(long txId, int currentCount) {
        int newCount = currentCount + 1;
        String nextRetryExpression;
        if (newCount == 1) nextRetryExpression = "DATE_ADD(NOW(), INTERVAL 5 MINUTE)";
        else if (newCount == 2) nextRetryExpression = "DATE_ADD(NOW(), INTERVAL 30 MINUTE)";
        else nextRetryExpression = null;

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.execute((TransactionStatus status) -> {
            try {
                String sql;
                if (nextRetryExpression != null) {
                    sql = "UPDATE " + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG
                            + " SET callback_status='RETRY', callback_retry_count=:count,"
                            + " callback_next_retry=" + nextRetryExpression
                            + " WHERE id=:id";
                } else {
                    sql = "UPDATE " + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG
                            + " SET callback_status='FAILED', callback_retry_count=:count"
                            + " WHERE id=:id";
                }
                MapSqlParameterSource p = new MapSqlParameterSource();
                p.addValue("id", txId);
                p.addValue("count", newCount);
                jdbcTemplate.update(sql, p);
            } catch (Exception e) {
                status.setRollbackOnly();
            }
            return null;
        });
    }

    private void markCallbackFailed(long txId, int currentCount) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.execute((TransactionStatus status) -> {
            try {
                String sql = "UPDATE " + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG
                        + " SET callback_status='FAILED', callback_retry_count=:count WHERE id=:id";
                MapSqlParameterSource p = new MapSqlParameterSource();
                p.addValue("id", txId);
                p.addValue("count", currentCount + 1);
                jdbcTemplate.update(sql, p);
            } catch (Exception e) {
                status.setRollbackOnly();
            }
            return null;
        });
    }
}
