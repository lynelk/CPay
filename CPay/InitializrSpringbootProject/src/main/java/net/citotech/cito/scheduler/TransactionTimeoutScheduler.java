package net.citotech.cito.scheduler;

import net.citotech.cito.Common;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.Model.Transaction;
import net.citotech.cito.Model.TxCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Scheduler that auto-resolves PENDING transactions that have exceeded the timeout threshold.
 * Default timeout: 30 minutes (configurable per gateway via settings).
 */
@Component
public class TransactionTimeoutScheduler {

    private static final Logger logger = Logger.getLogger(TransactionTimeoutScheduler.class.getName());
    private static final int DEFAULT_TIMEOUT_MINUTES = 30;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * Runs every 5 minutes to find and timeout stuck PENDING transactions.
     */
    @Scheduled(fixedDelay = 300000)
    public void timeoutStalePendingTransactions() {
        try {
            String sql = "SELECT * FROM " + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG
                    + " WHERE status = 'PENDING'"
                    + " AND created_on <= DATE_SUB(NOW(), INTERVAL :timeout_minutes MINUTE)"
                    + " LIMIT 100";

            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("timeout_minutes", DEFAULT_TIMEOUT_MINUTES);

            List<Transaction> staleTxs = jdbcTemplate.query(sql, params, (rs, rowNum) -> {
                Transaction t = new Transaction();
                t.setId(rs.getLong("id"));
                t.setMerchant_id(rs.getString("merchant_id"));
                t.setGateway_id(rs.getString("gateway_id"));
                t.setOriginal_amount(rs.getDouble("original_amount"));
                t.setCharges(rs.getDouble("charges"));
                t.setStatus(rs.getString("status"));
                t.setTx_unique_id(rs.getString("tx_unique_id"));
                t.setTx_gateway_ref(rs.getString("tx_gateway_ref"));
                t.setTx_merchant_ref(rs.getString("tx_merchant_ref"));
                t.setPayer_number(rs.getString("payer_number"));
                t.setTx_type(rs.getString("tx_type"));
                t.setCreated_on(rs.getString("created_on"));
                t.setUpdated_on(rs.getString("updated_on"));
                t.setCallback_url(rs.getString("callback_url"));
                t.setTx_merchant_description(rs.getString("tx_merchant_description"));
                t.setTx_update_trace(rs.getString("tx_update_trace"));
                return t;
            });

            for (Transaction tx : staleTxs) {
                timeoutTransaction(tx);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "TransactionTimeoutScheduler error: " + e.getMessage(), e);
        }
    }

    private void timeoutTransaction(Transaction tx) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.execute((TransactionStatus status) -> {
            try {
                tx.setStatus("FAILED");
                tx.setTx_update_trace("AUTO_TIMEOUT: Transaction exceeded " + DEFAULT_TIMEOUT_MINUTES + " minute pending limit");
                tx.setResolved_by("SYSTEM_TIMEOUT");

                String sql = "UPDATE " + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG
                        + " SET status=:status, tx_update_trace=:trace, resolved_by=:resolved_by"
                        + " WHERE id=:id AND status='PENDING'";
                MapSqlParameterSource p = new MapSqlParameterSource();
                p.addValue("id", tx.getId());
                p.addValue("status", "FAILED");
                p.addValue("trace", tx.getTx_update_trace());
                p.addValue("resolved_by", "SYSTEM_TIMEOUT");
                int updated = jdbcTemplate.update(sql, p);

                if (updated > 0) {
                    logger.log(Level.INFO, "Timed out PENDING transaction: " + tx.getTx_unique_id());
                    Merchant merchant = Common.getMerchantById(tx.getMerchant_id(), jdbcTemplate);
                    if (merchant != null && tx.getCallback_url() != null && !tx.getCallback_url().isEmpty()) {
                        TxCallback txCallback = new TxCallback(tx, merchant);
                        txCallback.start(jdbcTemplate, transactionManager);
                    }
                }
            } catch (Exception e) {
                status.setRollbackOnly();
                logger.log(Level.SEVERE, "Error timing out transaction " + tx.getId() + ": " + e.getMessage(), e);
            }
            return null;
        });
    }
}
