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

import java.security.*;
import java.util.Base64;
import java.util.HashMap;
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
        this.sql_update = " UPDATE "+Common.DB_TABLE_MERCHANT_TRANSACTION_LOG+" "
                + " SET status=:status, tx_update_trace=:tx_update_trace, "
                + " tx_gateway_ref=:tx_gateway_ref ";
    }
    
    public void start(NamedParameterJdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        Thread thread = new Thread(){
            public void run(){
                String amountToSign = tx.getOriginal_amount()+"";
                String signedData = tx.getPayer_number()+amountToSign
                        +tx.getCreated_on()+tx.getTx_merchant_ref()+tx.getStatus()
                        +tx.getTx_merchant_description()+tx.getTx_gateway_ref();

                //String signedData_ = Common.generateSha256String(signedData);

                if (merchant.getPrivate_key()== null || merchant.getPrivate_key().isEmpty()) {
                    return;
                }
                try {
                    //Now verify signature.
                    Signature sign = Signature.getInstance("SHA256withRSA");
                    String base64_private_key = merchant.getPrivate_key();
                    base64_private_key = base64_private_key.replace("-----BEGIN PRIVATE KEY-----\n", "");
                    String base64_cleaned = base64_private_key.replace("\n-----END PRIVATE KEY-----\n", "");

                    PrivateKey privateKey = Common.getPrivateKeyFromBase64String(base64_cleaned);
                    sign.initSign(privateKey);
                    sign.update(signedData.getBytes());
                    byte[] digitalSignature = sign.sign();
                    JSONObject jObject = new JSONObject();
                    jObject.put("amount", amountToSign);
                    jObject.put("payer_number", tx.getPayer_number());
                    jObject.put("reference", tx.getTx_merchant_ref());
                    jObject.put("network_ref", tx.getTx_gateway_ref());
                    jObject.put("status", tx.getStatus());
                    jObject.put("description", tx.getTx_merchant_description());
                    jObject.put("completed_on", tx.getUpdated_on());
                    jObject.put("created_on", tx.getCreated_on());
                    jObject.put("SignedData", signedData);
                    jObject.put("signature", Base64.getEncoder().encodeToString(digitalSignature));

                    String requestData = jObject.toString();
                    String url = tx.getCallback_url();
                    //Now make the callback request.
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Content-Type", "application/json");

                    HttpRequestResponse rs = Common.doHttpRequest("POST", url, requestData, headers);
                    if (rs != null) {
                        String sql_update_final =  sql_update+", callback_trace=:callback_trace "
                                + " WHERE id='"+tx.getId()+"'";
                        MapSqlParameterSource parameters_ = new MapSqlParameterSource();
                        parameters_.addValue("tx_update_trace", tx.getTx_update_trace());
                        parameters_.addValue("status", tx.getStatus());
                        parameters_.addValue("tx_gateway_ref", tx.getTx_gateway_ref());
                        parameters_.addValue("callback_trace", rs.toString());

                        //Now update the trace of this transaction.
                        String result = template.execute(new TransactionCallback<String>() {
                            @Override
                            public String doInTransaction(TransactionStatus status) {
                                try {
                                    jdbcTemplate.update(sql_update_final, parameters_);
                                    return "success";
                                } catch (Exception e) {
                                    //transactionManager.rollback(status);
                                    status.setRollbackOnly();
                                    Logger.getLogger(AuthenticationController.class.getName())
                                            .log(Level.SEVERE, "INTERNAL ERROR: "+e.getMessage(), "");
                                    return GeneralException
                                            .getError("102", GeneralException.ERRORS_102);
                                }
                            }
                        });
                        Logger.getLogger(TransactionsLogController.class.getName()).log(Level.SEVERE, "Callback Results: "+result, "");
                    }

                } catch (NoSuchAlgorithmException ex) {
                    Logger.getLogger(TransactionsLogController.class.getName()).log(Level.SEVERE, null, ex);
                } catch (InvalidKeyException ex) {
                    Logger.getLogger(TransactionsLogController.class.getName()).log(Level.SEVERE, null, ex);
                } catch (SignatureException ex) {
                    Logger.getLogger(TransactionsLogController.class.getName()).log(Level.SEVERE, null, ex);
                } catch (JSONException ex) {
                    Logger.getLogger(TransactionsLogController.class.getName()).log(Level.SEVERE, null, ex);
                }
                //System.out.println("Thread Running");
            }
        };
        thread.start();
    }
}