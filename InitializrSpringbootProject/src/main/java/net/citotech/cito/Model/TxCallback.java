package net.citotech.cito.Model;

import net.citotech.cito.AuthenticationController;
import net.citotech.cito.Common;
import net.citotech.cito.GeneralException;
import net.citotech.cito.TransactionsLogController;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TxCallback {
    public Transaction tx;
    public Merchant merchant;
    public String sql_update;

    public TxCallback(Transaction tx, Merchant merchant) {
        this.tx = tx;
        this.merchant = merchant;
        this.sql_update = " UPDATE " + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG + " "
                + " SET status=:status, tx_update_trace=:tx_update_trace, "
                + " tx_gateway_ref=:tx_gateway_ref ";
    }

    private static String maskPhoneNumber(String phone) {
        if (phone == null || phone.length() < 6) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 3);
    }

    private boolean isCallbackAlreadyDelivered(NamedParameterJdbcTemplate jdbcTemplate) {
        try {
            String sql = "SELECT callback_status FROM " + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG
                    + " WHERE id=:id LIMIT 1";
            MapSqlParameterSource p = new MapSqlParameterSource();
            p.addValue("id", tx.getId());
            List<String> statuses = jdbcTemplate.query(sql, p,
                    (rs, rowNum) -> rs.getString("callback_status"));
            return !statuses.isEmpty() && "SUCCESS".equals(statuses.get(0));
        } catch (Exception e) {
            return false;
        }
    }

    private void markCallbackFailed(NamedParameterJdbcTemplate jdbcTemplate, TransactionTemplate template) {
        template.execute((TransactionStatus status) -> {
            try {
                String sql = "UPDATE " + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG
                        + " SET callback_status='FAILED' WHERE id=:id";
                MapSqlParameterSource p = new MapSqlParameterSource();
                p.addValue("id", tx.getId());
                jdbcTemplate.update(sql, p);
            } catch (Exception e) {
                status.setRollbackOnly();
            }
            return null;
        });
    }

    public void start(NamedParameterJdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        Thread thread = new Thread() {
            public void run() {
                // Idempotency: do not re-fire if a previous attempt already succeeded
                if (isCallbackAlreadyDelivered(jdbcTemplate)) {
                    return;
                }

                String amountToSign = tx.getOriginal_amount() + "";
                String maskedPayer  = maskPhoneNumber(tx.getPayer_number());
                String signedData   = tx.getPayer_number() + amountToSign
                        + tx.getCreated_on() + tx.getTx_merchant_ref() + tx.getStatus()
                        + tx.getTx_merchant_description() + tx.getTx_gateway_ref();

                try {
                    JSONObject jObject = new JSONObject();
                    jObject.put("amount",       amountToSign);
                    jObject.put("payer_number", maskedPayer);
                    jObject.put("reference",    tx.getTx_merchant_ref());
                    jObject.put("network_ref",  tx.getTx_gateway_ref());
                    jObject.put("status",       tx.getStatus());
                    jObject.put("description",  tx.getTx_merchant_description());
                    jObject.put("completed_on", tx.getUpdated_on());
                    jObject.put("created_on",   tx.getCreated_on());
                    jObject.put("currency",     tx.getCurrency());
                    jObject.put("SignedData",   signedData);

                    String hmacSecret = merchant.getHmac_secret();
                    if (hmacSecret != null && !hmacSecret.isEmpty()) {
                        // HMAC-SHA256 (preferred for new integrations)
                        Mac mac = Mac.getInstance("HmacSHA256");
                        mac.init(new SecretKeySpec(hmacSecret.getBytes("UTF-8"), "HmacSHA256"));
                        byte[] hmacBytes = mac.doFinal(signedData.getBytes("UTF-8"));
                        jObject.put("signature",           Base64.getEncoder().encodeToString(hmacBytes));
                        jObject.put("signature_algorithm", "HMAC-SHA256");
                    } else if (merchant.getPrivate_key() != null && !merchant.getPrivate_key().isEmpty()) {
                        // RSA-SHA256 (legacy)
                        Signature sign = Signature.getInstance("SHA256withRSA");
                        String base64Key = merchant.getPrivate_key()
                                .replace("-----BEGIN PRIVATE KEY-----\n", "")
                                .replace("\n-----END PRIVATE KEY-----\n", "");
                        PrivateKey privateKey = Common.getPrivateKeyFromBase64String(base64Key);
                        sign.initSign(privateKey);
                        sign.update(signedData.getBytes());
                        byte[] digitalSignature = sign.sign();
                        jObject.put("signature",           Base64.getEncoder().encodeToString(digitalSignature));
                        jObject.put("signature_algorithm", "RSA-SHA256");
                    } else {
                        // No signing configured; skip callback
                        return;
                    }

                    String url = tx.getCallback_url();
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Content-Type", "application/json");

                    HttpRequestResponse rs = Common.doHttpRequest("POST", url, jObject.toString(), headers);
                    if (rs != null) {
                        String sql_update_final = sql_update
                                + ", callback_trace=:callback_trace"
                                + ", callback_status='SUCCESS'"
                                + " WHERE id=:id";
                        MapSqlParameterSource parameters_ = new MapSqlParameterSource();
                        parameters_.addValue("id",              tx.getId());
                        parameters_.addValue("tx_update_trace", tx.getTx_update_trace());
                        parameters_.addValue("status",          tx.getStatus());
                        parameters_.addValue("tx_gateway_ref",  tx.getTx_gateway_ref());
                        parameters_.addValue("callback_trace",  rs.toString());

                        String result = template.execute(new TransactionCallback<String>() {
                            @Override
                            public String doInTransaction(TransactionStatus status) {
                                try {
                                    jdbcTemplate.update(sql_update_final, parameters_);
                                    return "success";
                                } catch (Exception e) {
                                    status.setRollbackOnly();
                                    Logger.getLogger(AuthenticationController.class.getName())
                                            .log(Level.SEVERE, "INTERNAL ERROR: " + e.getMessage(), "");
                                    return GeneralException.getError("102", GeneralException.ERRORS_102);
                                }
                            }
                        });
                        Logger.getLogger(TransactionsLogController.class.getName())
                                .log(Level.INFO, "Callback result: " + result, "");
                    } else {
                        markCallbackFailed(jdbcTemplate, template);
                    }
                } catch (Exception ex) {
                    Logger.getLogger(TransactionsLogController.class.getName()).log(Level.SEVERE, null, ex);
                    markCallbackFailed(jdbcTemplate, template);
                }
            }
        };
        thread.start();
    }
}
