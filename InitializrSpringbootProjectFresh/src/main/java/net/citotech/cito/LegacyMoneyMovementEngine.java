package net.citotech.cito;

import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import net.citotech.cito.Model.*;
import net.citotech.cito.async.ManagedAsyncTasks;
import net.citotech.cito.callback.CallbackTaskRepository;
import net.citotech.cito.merchant.MerchantKeyCryptoRegistry;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/** Legacy pay-in/pay-out execution engine extracted from Common while preserving the v1 compatibility signatures. */
public final class LegacyMoneyMovementEngine {
    private LegacyMoneyMovementEngine() {}

private static String buildIdempotentReplayResponse(Transaction existingTx) {
        net.citotech.cito.Model.TransactionStatus status =
                net.citotech.cito.Model.TransactionStatus.fromString(existingTx.getStatus());
        if (status == null || !status.isTerminal()) {
            return GeneralException.getError(
                    "121",
                    String.format(GeneralException.ERRORS_121, existingTx.getTx_merchant_ref()));
        }

        GateWayResponse replay = new GateWayResponse();
        replay.setOurUniqueTxId(existingTx.getTx_unique_id());
        replay.setStatus(
                status == net.citotech.cito.Model.TransactionStatus.SUCCESSFUL ? "OK" : "ERROR");
        replay.setTransactionStatus(existingTx.getStatus() == null ? "" : existingTx.getStatus());
        replay.setNetworkId(
                existingTx.getTx_gateway_ref() == null ? "" : existingTx.getTx_gateway_ref());
        replay.setRequestTrace(
                existingTx.getTx_request_trace() == null ? "" : existingTx.getTx_request_trace());
        if (existingTx.getSafaricomRequestReference() != null) {
            replay.setSafaricomRequestReference(existingTx.getSafaricomRequestReference());
        }

        if (status == net.citotech.cito.Model.TransactionStatus.SUCCESSFUL) {
            replay.setMessage(GeneralSuccessResponse.SUCCESS_000);
            return GeneralSuccessResponse.getApiTxMessage(
                    "000", GeneralSuccessResponse.SUCCESS_000, replay);
        }
        replay.setMessage(GeneralException.ERRORS_143);
        return GeneralException.getApiTxMessage("143", GeneralException.ERRORS_143, replay);
    }

private static String authorizeLegacyRisk(
            Transaction newTx, Merchant merchant, String direction) {
        net.citotech.cito.api.v2.dto.PaymentRequest request =
                new net.citotech.cito.api.v2.dto.PaymentRequest();
        request.setMerchantNumber(merchant.getAccount_number());
        request.setAmount(String.valueOf(newTx.getOriginal_amount()));
        request.setCurrency(
                newTx.getCurrency() == null || newTx.getCurrency().isEmpty()
                        ? "UGX"
                        : newTx.getCurrency());
        request.setReference(newTx.getTx_merchant_ref());
        net.citotech.cito.api.v2.dto.PaymentPartyRequest party =
                new net.citotech.cito.api.v2.dto.PaymentPartyRequest();
        party.setValue(newTx.getPayer_number());
        if ("PAYOUT".equalsIgnoreCase(direction)) {
            request.setPayee(party);
        } else {
            request.setPayer(party);
        }
        try {
            net.citotech.cito.compliance.RiskDecisionRegistry.authorize(
                    merchant, request, direction);
            return null;
        } catch (net.citotech.cito.gateway.PaymentGatewayException ex) {
            return GeneralException.getError(
                    "148", String.format(GeneralException.ERRORS_148, ex.getMessage()));
        }
    }

public static String doPayIn(
            Transaction newTx,
            Merchant merchant,
            NamedParameterJdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        return doPayIn(newTx, merchant, jdbcTemplate, transactionManager, false);
    }

public static String doPayIn(
            Transaction newTx,
            Merchant merchant,
            NamedParameterJdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            boolean skipRiskCheck) {

        // First check if there is tx with this reference on merchant.
        Transaction tx =
                Common.getMerchantTxByTheirRef(
                        newTx.getTx_merchant_ref(), merchant.getId() + "", jdbcTemplate);
        if (tx != null) {
            return buildIdempotentReplayResponse(tx);
        }

        if (!skipRiskCheck) {
            String riskError = authorizeLegacyRisk(newTx, merchant, "COLLECT");
            if (riskError != null) {
                return riskError;
            }
        }

        boolean useMerchantCreds = Common.useMerchantProviderCredentials(jdbcTemplate);
        Merchant[] gwAccounts = Common.resolveGatewayAccounts(useMerchantCreds, jdbcTemplate);
        Merchant float_stock_account = gwAccounts[0];
        Merchant suspense_stock_account = gwAccounts[1];
        Merchant revenue_stock_account = gwAccounts[2];

        String[] bType = Balance.getBalanceTypeByGatewayId(newTx.getGateway_id());
        String balance_type = bType[0];

        MapSqlParameterSource parametersBalanceSql = new MapSqlParameterSource();
        parametersBalanceSql.addValue("merchant_id", merchant.getId());

        // Now add the user to database
        String sql = "INSERT INTO " + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG + " ";
        String sql_set =
                " SET `merchant_id`=:merchant_id,"
                        + " `gateway_id`=:gateway_id, "
                        + " `original_amount`=:original_amount,"
                        + " `tx_type`=:tx_type,"
                        + " `charges`=:charges,"
                        + " `tx_description`=:tx_description,"
                        + " `tx_merchant_description`=:tx_merchant_description,"
                        + " `tx_unique_id`=:tx_unique_id,"
                        + " `tx_gateway_ref`=:tx_gateway_ref,"
                        + " `tx_merchant_ref`=:tx_merchant_ref,"
                        + " `payer_number`=:payer_number,"
                        + " `tx_request_trace`=:tx_request_trace,"
                        + " `tx_update_trace`=:tx_update_trace,"
                        + " `charging_method`=:charging_method,"
                        + " `status`=:status,"
                        + " `callback_url`=:callback_url,"
                        + " `originate_ip`=:originate_ip,"
                        + "`safaricom_request_reference`=:safaricom_request_reference,"
                        + " `currency`=:currency,"
                        + " `callback_status`='PENDING',"
                        + " `tx_cost`=:tx_cost";

        final String sql_insert = sql + sql_set;

        String sql_update = " UPDATE " + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG + " ";

        MapSqlParameterSource parameters = new MapSqlParameterSource();

        parameters.addValue("merchant_id", merchant.getId());
        parameters.addValue("gateway_id", newTx.getGateway_id());
        parameters.addValue("tx_description", newTx.getTx_merchant_description());
        parameters.addValue("tx_merchant_description", newTx.getTx_merchant_description());
        parameters.addValue("original_amount", newTx.getOriginal_amount());
        parameters.addValue("tx_type", newTx.getTx_type());
        parameters.addValue("tx_cost", newTx.getTx_cost());

        parameters.addValue("tx_cost", newTx.getTx_cost());
        parameters.addValue("status", newTx.getStatus());
        parameters.addValue("charging_method", newTx.getCharging_method());
        parameters.addValue("payer_number", newTx.getPayer_number());
        parameters.addValue("tx_merchant_ref", newTx.getTx_merchant_ref());
        parameters.addValue("tx_gateway_ref", newTx.getTx_gateway_ref());
        parameters.addValue("tx_unique_id", newTx.getTx_unique_id());
        parameters.addValue("charges", newTx.getCharges());

        parameters.addValue("tx_request_trace", newTx.getTx_request_trace());
        parameters.addValue("tx_update_trace", newTx.getTx_update_trace());
        parameters.addValue("charges", newTx.getCharges());
        parameters.addValue("callback_url", newTx.getCallback_url());
        parameters.addValue("originate_ip", newTx.getOriginate_ip());
        parameters.addValue("safaricom_request_reference", "");
        parameters.addValue("currency", newTx.getCurrency() != null ? newTx.getCurrency() : "");

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        String result =
                template.execute(
                        new TransactionCallback<String>() {
                            @Override
                            public String doInTransaction(TransactionStatus status) {
                                try {

                                    KeyHolder keyHolder = new GeneratedKeyHolder();
                                    // long userId;
                                    jdbcTemplate.update(sql_insert, parameters, keyHolder);
                                    // Now insert privileges
                                    BigInteger txId = (BigInteger) keyHolder.getKey();
                                    newTx.setId(txId.longValue());

                                    return "success";
                                } catch (Exception e) {
                                    // transactionManager.rollback(status);
                                    status.setRollbackOnly();
                                    Logger.getLogger(AuthenticationController.class.getName())
                                            .log(
                                                    Level.SEVERE,
                                                    "INTERNAL ERROR: " + e.getMessage(),
                                                    "");
                                    return GeneralException.getError(
                                            "102", GeneralException.ERRORS_102);
                                }
                            }
                        });

        if (result.equals("success")) {
            // Now make the actual transaction
            DoPayGateway gw = new DoPayGateway();

            final GateWayResponse pResponse =
                    gw.runPayGatewayDoPayIn(
                            jdbcTemplate,
                            newTx.getPayer_number(),
                            newTx.getOriginal_amount(),
                            newTx.getTx_unique_id(),
                            newTx.getTx_description(),
                            Long.parseLong(newTx.getMerchant_id()));

            if (pResponse != null) {
                String trace = pResponse.getRequestTrace();
                // Now update this transaction in DB
                newTx.setTx_request_trace(trace);
                newTx.setStatus(pResponse.getTransactionStatus());
                newTx.setTx_gateway_ref(pResponse.getNetworkId());
                if (!pResponse.getSafaricomRequestReference().isEmpty()) {
                    newTx.setSafaricomRequestReference(pResponse.getSafaricomRequestReference());
                }

                String sql_update_final = sql_update + sql_set + " WHERE id=:id";

                // Update parameters
                parameters.addValue("id", newTx.getId());
                parameters.addValue("tx_request_trace", newTx.getTx_request_trace());
                parameters.addValue("status", newTx.getStatus());
                parameters.addValue("tx_gateway_ref", newTx.getTx_gateway_ref());
                if (!newTx.getSafaricomRequestReference().isEmpty()) {
                    parameters.addValue(
                            "safaricom_request_reference", newTx.getSafaricomRequestReference());
                }

                result =
                        template.execute(
                                new TransactionCallback<String>() {
                                    @Override
                                    public String doInTransaction(TransactionStatus status) {
                                        try {

                                            jdbcTemplate.update(sql_update_final, parameters);
                                            String res_string = "";

                                            if (pResponse
                                                    .getTransactionStatus()
                                                    .equals("SUCCESSFUL")) {
                                                // Credit this customer's account.
                                                Statement newTxS = new Statement();
                                                newTxS.setTransactions_log_id(newTx.getId());
                                                newTxS.setAmount(newTx.getOriginal_amount());
                                                newTxS.setGateway_id(newTx.getGateway_id());
                                                newTxS.setNarritive(newTx.getTx_type());
                                                newTxS.setTransactions_log_id(newTx.getId());
                                                newTxS.setMerchant_id(
                                                        Long.parseLong(newTx.getMerchant_id()));
                                                newTxS.setDescription(newTx.getTx_description());
                                                newTxS.setRecorded_by("SYSTEM");
                                                newTxS.setTx_type("CR");

                                                res_string =
                                                        Common.recordStatementTx(
                                                                newTxS,
                                                                balance_type,
                                                                jdbcTemplate,
                                                                transactionManager);
                                                if (!res_string.equals("success")) {
                                                    return res_string;
                                                }

                                                if (newTx.getCharges() > 0) {
                                                    newTxS = new Statement();
                                                    newTxS.setTransactions_log_id(newTx.getId());
                                                    newTxS.setAmount(newTx.getCharges());
                                                    newTxS.setGateway_id(newTx.getGateway_id());

                                                    newTxS.setNarritive(
                                                            Transaction.TX_TYPE_PAYIN_CHARGE);
                                                    newTxS.setTransactions_log_id(newTx.getId());
                                                    newTxS.setMerchant_id(merchant.getId());
                                                    newTxS.setDescription(
                                                            newTx.getTx_description());
                                                    newTxS.setRecorded_by("SYSTEM");
                                                    newTxS.setTx_type("DR");

                                                    res_string =
                                                            Common.recordStatementTx(
                                                                    newTxS,
                                                                    balance_type,
                                                                    jdbcTemplate,
                                                                    transactionManager);
                                                    if (!res_string.equals("success")) {
                                                        return res_string;
                                                    }
                                                } // end if (charges > 0)

                                                if (!useMerchantCreds) {
                                                    // Now record this revenue account.
                                                    if (newTx.getCharges() > 0) {
                                                        newTxS = new Statement();
                                                        newTxS.setTransactions_log_id(
                                                                newTx.getId());
                                                        newTxS.setAmount(newTx.getCharges());
                                                        newTxS.setGateway_id(newTx.getGateway_id());

                                                        newTxS.setNarritive(
                                                                Transaction.TX_TYPE_PAYIN_REVENUE);
                                                        newTxS.setTransactions_log_id(
                                                                newTx.getId());
                                                        newTxS.setMerchant_id(
                                                                revenue_stock_account.getId());
                                                        newTxS.setDescription(
                                                                newTx.getTx_description());
                                                        newTxS.setRecorded_by("SYSTEM");
                                                        newTxS.setTx_type("CR");

                                                        res_string =
                                                                Common.recordStatementTx(
                                                                        newTxS,
                                                                        balance_type,
                                                                        jdbcTemplate,
                                                                        transactionManager);
                                                        if (!res_string.equals("success")) {
                                                            return res_string;
                                                        }
                                                    } // end if (charges > 0)

                                                    // Now increase stock account.
                                                    newTxS = new Statement();
                                                    newTxS.setTransactions_log_id(newTx.getId());
                                                    newTxS.setAmount(newTx.getOriginal_amount());
                                                    newTxS.setGateway_id(newTx.getGateway_id());

                                                    newTxS.setNarritive(Transaction.TX_TYPE_PAYIN);
                                                    newTxS.setTransactions_log_id(newTx.getId());
                                                    newTxS.setMerchant_id(
                                                            float_stock_account.getId());
                                                    newTxS.setDescription(
                                                            newTx.getTx_description());
                                                    newTxS.setRecorded_by("SYSTEM");
                                                    newTxS.setTx_type("CR");

                                                    res_string =
                                                            Common.recordStatementTx(
                                                                    newTxS,
                                                                    balance_type,
                                                                    jdbcTemplate,
                                                                    transactionManager);
                                                    if (!res_string.equals("success")) {
                                                        return res_string;
                                                    }
                                                } // end if (!useMerchantCreds)
                                            }

                                            return "success";
                                        } catch (Exception e) {
                                            // transactionManager.rollback(status);
                                            status.setRollbackOnly();
                                            Logger.getLogger(
                                                            AuthenticationController.class
                                                                    .getName())
                                                    .log(
                                                            Level.SEVERE,
                                                            "INTERNAL ERROR: " + e.getMessage(),
                                                            "");
                                            return GeneralException.getError(
                                                    "102", GeneralException.ERRORS_102);
                                        }
                                    }
                                });
                if (result.equals("success")) {
                    pResponse.setOurUniqueTxId(newTx.getTx_unique_id());
                    return GeneralSuccessResponse.getApiTxMessage(
                            "000", GeneralSuccessResponse.SUCCESS_000, pResponse);
                } else {
                    return result;
                }
            } else {
                GateWayResponse pResponse_ = new GateWayResponse();
                pResponse_.setHttpStatus("");
                pResponse_.setStatus("ERROR");
                pResponse_.setRequestTrace("");
                pResponse_.setTransactionStatus("");
                pResponse_.setNetworkId("");
                pResponse_.setMessage(GeneralException.ERRORS_102);
                return GeneralException.getApiTxMessage(
                        "102", GeneralException.ERRORS_102, pResponse_);
            }
        } else {
            return result;
        }
    }

public static String doPayOut(
            Transaction newTx,
            Merchant merchant,
            NamedParameterJdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        return doPayOut(newTx, merchant, jdbcTemplate, transactionManager, false);
    }

public static String doPayOut(
            Transaction newTx,
            Merchant merchant,
            NamedParameterJdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            boolean skipRiskCheck) {

        boolean useMerchantCreds = Common.useMerchantProviderCredentials(jdbcTemplate);
        Merchant[] gwAccounts = Common.resolveGatewayAccounts(useMerchantCreds, jdbcTemplate);
        Merchant float_stock_account = gwAccounts[0];
        Merchant suspense_stock_account = gwAccounts[1];
        Merchant revenue_stock_account = gwAccounts[2];

        // First check if there is tx with this reference on merchant.
        Transaction tx =
                Common.getMerchantTxByTheirRef(
                        newTx.getTx_merchant_ref(), merchant.getId() + "", jdbcTemplate);
        if (tx != null) {
            return buildIdempotentReplayResponse(tx);
        }

        if (!skipRiskCheck) {
            String riskError = authorizeLegacyRisk(newTx, merchant, "PAYOUT");
            if (riskError != null) {
                return riskError;
            }
        }

        MapSqlParameterSource parametersBalanceSql = new MapSqlParameterSource();
        parametersBalanceSql.addValue("merchant_id", merchant.getId());

        // Now add the user to database
        String sql = "INSERT INTO " + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG + " ";
        String sql_set =
                " SET `merchant_id`=:merchant_id,"
                        + " `gateway_id`=:gateway_id, "
                        + " `original_amount`=:original_amount,"
                        + " `tx_type`=:tx_type,"
                        + " `charges`=:charges,"
                        + " `tx_description`=:tx_description,"
                        + " `tx_merchant_description`=:tx_merchant_description,"
                        + " `tx_unique_id`=:tx_unique_id,"
                        + " `tx_gateway_ref`=:tx_gateway_ref,"
                        + " `tx_merchant_ref`=:tx_merchant_ref,"
                        + " `payer_number`=:payer_number,"
                        + " `tx_request_trace`=:tx_request_trace,"
                        + " `tx_update_trace`=:tx_update_trace,"
                        + " `charging_method`=:charging_method,"
                        + " `status`=:status,"
                        + " `callback_url`=:callback_url,"
                        + " `originate_ip`=:originate_ip,"
                        + " `safaricom_request_reference`=:safaricom_request_reference,"
                        + " `currency`=:currency,"
                        + " `callback_status`='PENDING',"
                        + " `tx_cost`=:tx_cost";

        if (newTx.getMerchant_batch_transactions_log_id() != null
                && newTx.getMerchant_batch_transactions_log_id() > 0) {
            sql_set += ", merchant_batch_transactions_log_id=:batch_id ";
        }

        if (newTx.getBeneficiary_id() != null && newTx.getBeneficiary_id() > 0) {
            sql_set += ", beneficiary_id=:beneficiary_id ";
        }

        final String sql_insert = sql + sql_set;

        String sql_update = " UPDATE " + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG + " ";

        MapSqlParameterSource parameters = new MapSqlParameterSource();

        parameters.addValue("merchant_id", merchant.getId());
        parameters.addValue("gateway_id", newTx.getGateway_id());
        parameters.addValue("tx_description", newTx.getTx_merchant_description());
        parameters.addValue("tx_merchant_description", newTx.getTx_merchant_description());
        parameters.addValue("original_amount", newTx.getOriginal_amount());
        parameters.addValue("tx_type", newTx.getTx_type());
        parameters.addValue("tx_cost", newTx.getTx_cost());

        parameters.addValue("tx_cost", newTx.getTx_cost());
        parameters.addValue("status", newTx.getStatus());
        parameters.addValue("charging_method", newTx.getCharging_method());
        parameters.addValue("payer_number", newTx.getPayer_number());
        parameters.addValue("tx_merchant_ref", newTx.getTx_merchant_ref());
        parameters.addValue("tx_gateway_ref", newTx.getTx_gateway_ref());
        parameters.addValue("tx_unique_id", newTx.getTx_unique_id());
        parameters.addValue("charges", newTx.getCharges());
        parameters.addValue("callback_url", newTx.getCallback_url());
        parameters.addValue("safaricom_request_reference", newTx.getSafaricomRequestReference());
        parameters.addValue("currency", newTx.getCurrency() != null ? newTx.getCurrency() : "");

        parameters.addValue("tx_request_trace", newTx.getTx_request_trace());
        parameters.addValue("tx_update_trace", newTx.getTx_update_trace());
        parameters.addValue("charges", newTx.getCharges());
        parameters.addValue("callback_url", newTx.getCallback_url());
        parameters.addValue("originate_ip", newTx.getOriginate_ip());

        if (newTx.getMerchant_batch_transactions_log_id() != null
                && newTx.getMerchant_batch_transactions_log_id() > 0) {
            parameters.addValue("batch_id", newTx.getMerchant_batch_transactions_log_id());
        }

        if (newTx.getBeneficiary_id() != null && newTx.getBeneficiary_id() > 0) {
            parameters.addValue("beneficiary_id", newTx.getBeneficiary_id());
        }

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        String result =
                template.execute(
                        new TransactionCallback<String>() {
                            @Override
                            public String doInTransaction(TransactionStatus status) {
                                try {
                                    String result = "";
                                    KeyHolder keyHolder = new GeneratedKeyHolder();
                                    // long userId;
                                    jdbcTemplate.update(sql_insert, parameters, keyHolder);
                                    // Now insert privileges
                                    BigInteger txId = (BigInteger) keyHolder.getKey();
                                    newTx.setId(txId.longValue());

                                    // Remove the charges and put them to suspense account

                                    return "success";
                                } catch (Exception e) {
                                    // transactionManager.rollback(status);
                                    status.setRollbackOnly();
                                    Logger.getLogger(AuthenticationController.class.getName())
                                            .log(
                                                    Level.SEVERE,
                                                    "INTERNAL ERROR - SAVING TX: " + e.getMessage(),
                                                    "");
                                    return GeneralException.getError(
                                            "102", GeneralException.ERRORS_102);
                                }
                            }
                        });

        if (result.equals("success")) {

            // Transfer the amount to suspense account
            String[] bType = Balance.getBalanceTypeByGatewayId(newTx.getGateway_id());
            String balance_type = bType[0];

            Statement newTxStatement = new Statement();
            newTxStatement.setTransactions_log_id(newTx.getId());
            newTxStatement.setAmount(newTx.getOriginal_amount());
            newTxStatement.setGateway_id(newTx.getGateway_id());
            newTxStatement.setNarritive(Transaction.TX_TYPE_PAYOUT);
            newTxStatement.setTransactions_log_id(newTx.getId());
            newTxStatement.setMerchant_id(Long.parseLong(newTx.getMerchant_id()));
            newTxStatement.setDescription(newTx.getTx_description());
            newTxStatement.setRecorded_by("SYSTEM");
            newTxStatement.setTx_type("DR");

            result =
                    Common.recordStatementTx(
                            newTxStatement, balance_type, jdbcTemplate, transactionManager);
            if (!result.equals("success")) {
                return result;
            }

            if (!useMerchantCreds) {
                // Reduce the float stock account
                newTxStatement = new Statement();
                newTxStatement.setTransactions_log_id(newTx.getId());
                newTxStatement.setAmount(newTx.getOriginal_amount());
                newTxStatement.setGateway_id(newTx.getGateway_id());
                newTxStatement.setNarritive(Transaction.TX_TYPE_PAYOUT);
                newTxStatement.setTransactions_log_id(newTx.getId());
                newTxStatement.setMerchant_id(float_stock_account.getId());
                newTxStatement.setDescription(newTx.getTx_description());
                newTxStatement.setRecorded_by("SYSTEM");
                newTxStatement.setTx_type("DR");

                result =
                        Common.recordStatementTx(
                                newTxStatement, balance_type, jdbcTemplate, transactionManager);
                if (!result.equals("success")) {
                    return result;
                }

                // Credit the suspense account.
                newTxStatement = new Statement();
                newTxStatement.setTransactions_log_id(newTx.getId());
                newTxStatement.setAmount(newTx.getOriginal_amount());
                newTxStatement.setGateway_id(newTx.getGateway_id());
                newTxStatement.setNarritive(Transaction.TX_TYPE_PAYOUT);
                newTxStatement.setTransactions_log_id(newTx.getId());
                newTxStatement.setMerchant_id(suspense_stock_account.getId());
                newTxStatement.setDescription(newTx.getTx_description());
                newTxStatement.setRecorded_by("SYSTEM");
                newTxStatement.setTx_type("CR");

                result =
                        Common.recordStatementTx(
                                newTxStatement, balance_type, jdbcTemplate, transactionManager);
                if (!result.equals("success")) {
                    return result;
                }
            } // end if (!useMerchantCreds)

            if (newTx.getCharges() > 0) {
                // Dr account for this transaction's charge
                newTxStatement = new Statement();
                newTxStatement.setTransactions_log_id(newTx.getId());
                newTxStatement.setAmount(newTx.getCharges());
                newTxStatement.setGateway_id(newTx.getGateway_id());
                newTxStatement.setNarritive(Transaction.TX_TYPE_PAYOUT_CHARGE);
                newTxStatement.setTransactions_log_id(newTx.getId());
                newTxStatement.setMerchant_id(Long.parseLong(newTx.getMerchant_id()));
                newTxStatement.setDescription(newTx.getTx_description());
                newTxStatement.setRecorded_by("SYSTEM");
                newTxStatement.setTx_type("DR");

                result =
                        Common.recordStatementTx(
                                newTxStatement, balance_type, jdbcTemplate, transactionManager);
                if (!result.equals("success")) {
                    return result;
                }

                if (!useMerchantCreds) {
                    // Transfer charges to suspense account
                    newTxStatement = new Statement();
                    newTxStatement.setTransactions_log_id(newTx.getId());
                    newTxStatement.setAmount(newTx.getCharges());
                    newTxStatement.setGateway_id(newTx.getGateway_id());
                    newTxStatement.setNarritive(Transaction.TX_TYPE_PAYOUT_CHARGE);
                    newTxStatement.setTransactions_log_id(newTx.getId());
                    newTxStatement.setMerchant_id(suspense_stock_account.getId());
                    newTxStatement.setDescription(newTx.getTx_description());
                    newTxStatement.setRecorded_by("SYSTEM");
                    newTxStatement.setTx_type("CR");

                    result =
                            Common.recordStatementTx(
                                    newTxStatement, balance_type, jdbcTemplate, transactionManager);
                    if (!result.equals("success")) {
                        return result;
                    }
                } // end if (!useMerchantCreds)
            } // end if (charges > 0)

            // Now make the actual transaction
            DoPayGateway gw = new DoPayGateway();
            final GateWayResponse pResponse =
                    gw.runPayGatewayDoPayOut(
                            jdbcTemplate,
                            newTx.getPayer_number(),
                            newTx.getOriginal_amount(),
                            newTx.getTx_unique_id(),
                            newTx.getTx_description(),
                            Long.parseLong(newTx.getMerchant_id()));

            if (pResponse != null) {
                String trace = pResponse.getRequestTrace();
                // Now update this transaction in DB
                newTx.setTx_request_trace(trace);
                newTx.setStatus(pResponse.getTransactionStatus());
                newTx.setTx_gateway_ref(pResponse.getNetworkId());
                newTx.setSafaricomRequestReference(pResponse.getSafaricomRequestReference());

                String sql_update_final = sql_update + sql_set + " WHERE id=:id";

                // Update parameters
                parameters.addValue("id", newTx.getId());
                parameters.addValue(
                        "safaricom_request_reference", newTx.getSafaricomRequestReference());
                parameters.addValue("tx_request_trace", newTx.getTx_request_trace());
                parameters.addValue("status", newTx.getStatus());
                parameters.addValue("tx_gateway_ref", newTx.getTx_gateway_ref());

                result =
                        template.execute(
                                new TransactionCallback<String>() {
                                    @Override
                                    public String doInTransaction(TransactionStatus status) {
                                        try {

                                            jdbcTemplate.update(sql_update_final, parameters);

                                            String res_string = "";
                                            // If the transaction failed, the reverse the funds
                                            if (pResponse.getTransactionStatus().equals("FAILED")) {

                                                // Persisted compensation saga (audit B3): this
                                                // reversal is 2-5
                                                // separate statement writes. If one fails, the
                                                // whole DB
                                                // transaction below rolls back (recordStatementTx
                                                // marks it
                                                // rollback-only on failure) - but this saga record
                                                // commits
                                                // independently (REQUIRES_NEW), so a partial/stuck
                                                // reversal is
                                                // left in a queryable non-COMPLETED state rather
                                                // than only a log
                                                // line, and PayoutCompensationAlertScheduler can
                                                // alert on it.
                                                Long compensationSagaId =
                                                        net.citotech.cito.payout
                                                                .PayoutCompensationSagaRegistry
                                                                .start(
                                                                        newTx.getId(),
                                                                        newTx.getTx_unique_id(),
                                                                        merchant.getId(),
                                                                        5);

                                                Statement newTxS = new Statement();

                                                if (!useMerchantCreds) {
                                                    // Dr the amount on suspense account
                                                    newTxS = new Statement();
                                                    newTxS.setTransactions_log_id(newTx.getId());
                                                    newTxS.setAmount(newTx.getOriginal_amount());
                                                    newTxS.setGateway_id(newTx.getGateway_id());

                                                    newTxS.setNarritive(
                                                            Transaction.TX_TYPE_PAYOUT_REVERSAL);
                                                    newTxS.setTransactions_log_id(newTx.getId());
                                                    newTxS.setMerchant_id(
                                                            suspense_stock_account.getId());
                                                    newTxS.setDescription(
                                                            newTx.getTx_description());
                                                    newTxS.setRecorded_by("SYSTEM");
                                                    newTxS.setTx_type("DR");

                                                    res_string =
                                                            Common.recordStatementTx(
                                                                    newTxS,
                                                                    balance_type,
                                                                    jdbcTemplate,
                                                                    transactionManager);
                                                    if (!res_string.equals("success")) {
                                                        return res_string;
                                                    }
                                                    net.citotech.cito.payout
                                                            .PayoutCompensationSagaRegistry
                                                            .recordStepComplete(
                                                                    compensationSagaId,
                                                                    "DR_SUSPENSE");

                                                    if (newTx.getCharges() > 0) {
                                                        // DR the charge reversal on suspense
                                                        newTxS = new Statement();
                                                        newTxS.setTransactions_log_id(
                                                                newTx.getId());
                                                        newTxS.setAmount(newTx.getCharges());
                                                        newTxS.setGateway_id(newTx.getGateway_id());

                                                        newTxS.setNarritive(
                                                                Transaction
                                                                        .TX_TYPE_PAYOUT_CHARGE_REVERSAL);
                                                        newTxS.setTransactions_log_id(
                                                                newTx.getId());
                                                        newTxS.setMerchant_id(
                                                                suspense_stock_account.getId());
                                                        newTxS.setDescription(
                                                                newTx.getTx_description());
                                                        newTxS.setRecorded_by("SYSTEM");
                                                        newTxS.setTx_type("DR");
                                                        res_string =
                                                                Common.recordStatementTx(
                                                                        newTxS,
                                                                        balance_type,
                                                                        jdbcTemplate,
                                                                        transactionManager);
                                                        if (!res_string.equals("success")) {
                                                            return res_string;
                                                        }
                                                        net.citotech.cito.payout
                                                                .PayoutCompensationSagaRegistry
                                                                .recordStepComplete(
                                                                        compensationSagaId,
                                                                        "DR_CHARGE_SUSPENSE");
                                                    } // end if (charges > 0)
                                                } // end if (!useMerchantCreds)

                                                // CR the amount back to customer's account
                                                newTxS = new Statement();
                                                newTxS.setTransactions_log_id(newTx.getId());
                                                newTxS.setAmount(newTx.getOriginal_amount());
                                                newTxS.setGateway_id(newTx.getGateway_id());

                                                newTxS.setNarritive(
                                                        Transaction.TX_TYPE_PAYOUT_REVERSAL);
                                                newTxS.setTransactions_log_id(newTx.getId());
                                                newTxS.setMerchant_id(merchant.getId());
                                                newTxS.setDescription(newTx.getTx_description());
                                                newTxS.setRecorded_by("SYSTEM");
                                                newTxS.setTx_type("CR");
                                                res_string =
                                                        Common.recordStatementTx(
                                                                newTxS,
                                                                balance_type,
                                                                jdbcTemplate,
                                                                transactionManager);
                                                if (!res_string.equals("success")) {
                                                    return res_string;
                                                }
                                                net.citotech.cito.payout
                                                        .PayoutCompensationSagaRegistry
                                                        .recordStepComplete(
                                                                compensationSagaId, "CR_CUSTOMER");

                                                if (newTx.getCharges() > 0) {
                                                    // CR the charge back on customer's account
                                                    newTxS = new Statement();
                                                    newTxS.setTransactions_log_id(newTx.getId());
                                                    newTxS.setAmount(newTx.getCharges());
                                                    newTxS.setGateway_id(newTx.getGateway_id());

                                                    newTxS.setNarritive(
                                                            Transaction
                                                                    .TX_TYPE_PAYOUT_CHARGE_REVERSAL);
                                                    newTxS.setTransactions_log_id(newTx.getId());
                                                    newTxS.setMerchant_id(merchant.getId());
                                                    newTxS.setDescription(
                                                            newTx.getTx_description());
                                                    newTxS.setRecorded_by("SYSTEM");
                                                    newTxS.setTx_type("CR");
                                                    res_string =
                                                            Common.recordStatementTx(
                                                                    newTxS,
                                                                    balance_type,
                                                                    jdbcTemplate,
                                                                    transactionManager);
                                                    if (!res_string.equals("success")) {
                                                        return res_string;
                                                    }
                                                    net.citotech.cito.payout
                                                            .PayoutCompensationSagaRegistry
                                                            .recordStepComplete(
                                                                    compensationSagaId,
                                                                    "CR_CHARGE_CUSTOMER");
                                                } // end if (charges > 0)

                                                if (!useMerchantCreds) {
                                                    // Restore the float account
                                                    newTxS = new Statement();
                                                    newTxS.setTransactions_log_id(newTx.getId());
                                                    newTxS.setAmount(newTx.getOriginal_amount());
                                                    newTxS.setGateway_id(newTx.getGateway_id());

                                                    newTxS.setNarritive(
                                                            Transaction.TX_TYPE_PAYOUT_REVERSAL);
                                                    newTxS.setTransactions_log_id(newTx.getId());
                                                    newTxS.setMerchant_id(
                                                            float_stock_account.getId());
                                                    newTxS.setDescription(
                                                            newTx.getTx_description());
                                                    newTxS.setRecorded_by("SYSTEM");
                                                    newTxS.setTx_type("CR");
                                                    res_string =
                                                            Common.recordStatementTx(
                                                                    newTxS,
                                                                    balance_type,
                                                                    jdbcTemplate,
                                                                    transactionManager);
                                                    if (!res_string.equals("success")) {
                                                        return res_string;
                                                    }
                                                    net.citotech.cito.payout
                                                            .PayoutCompensationSagaRegistry
                                                            .recordStepComplete(
                                                                    compensationSagaId, "CR_FLOAT");
                                                } // end if (!useMerchantCreds)

                                                net.citotech.cito.payout
                                                        .PayoutCompensationSagaRegistry.complete(
                                                        compensationSagaId);

                                                Logger.getLogger(
                                                                AuthenticationController.class
                                                                        .getName())
                                                        .log(
                                                                Level.SEVERE,
                                                                "INTERNAL ERROR - TX STATUS UPDATE: "
                                                                        + pResponse.toString(),
                                                                "");
                                            } else if (pResponse
                                                    .getTransactionStatus()
                                                    .equals("SUCCESSFUL")) {
                                                if (!useMerchantCreds) {
                                                    // Record a settlement transaction for Payout
                                                    Statement newTxS = new Statement();
                                                    newTxS.setTransactions_log_id(newTx.getId());
                                                    newTxS.setAmount(newTx.getOriginal_amount());
                                                    newTxS.setGateway_id(newTx.getGateway_id());

                                                    newTxS.setNarritive(
                                                            Transaction.TX_TYPE_PAYOUT_SETTLEMENT);
                                                    newTxS.setTransactions_log_id(newTx.getId());
                                                    newTxS.setMerchant_id(
                                                            suspense_stock_account.getId());
                                                    newTxS.setDescription(
                                                            newTx.getTx_description());
                                                    newTxS.setRecorded_by("SYSTEM");
                                                    newTxS.setTx_type("DR");
                                                    res_string =
                                                            Common.recordStatementTx(
                                                                    newTxS,
                                                                    balance_type,
                                                                    jdbcTemplate,
                                                                    transactionManager);
                                                    if (!res_string.equals("success")) {
                                                        return res_string;
                                                    }

                                                    if (newTx.getCharges() > 0) {
                                                        // Record a settlement transaction for
                                                        // Payout charge
                                                        newTxS = new Statement();
                                                        newTxS.setTransactions_log_id(
                                                                newTx.getId());
                                                        newTxS.setAmount(newTx.getCharges());
                                                        newTxS.setGateway_id(newTx.getGateway_id());

                                                        newTxS.setNarritive(
                                                                Transaction
                                                                        .TX_TYPE_PAYOUT_CHARGE_SETTLEMENT);
                                                        newTxS.setTransactions_log_id(
                                                                newTx.getId());
                                                        newTxS.setMerchant_id(
                                                                suspense_stock_account.getId());
                                                        newTxS.setDescription(
                                                                newTx.getTx_description());
                                                        newTxS.setRecorded_by("SYSTEM");
                                                        newTxS.setTx_type("DR");
                                                        res_string =
                                                                Common.recordStatementTx(
                                                                        newTxS,
                                                                        balance_type,
                                                                        jdbcTemplate,
                                                                        transactionManager);
                                                        if (!res_string.equals("success")) {
                                                            return res_string;
                                                        }

                                                        // Record Revenue to revenue account
                                                        newTxS = new Statement();
                                                        newTxS.setTransactions_log_id(
                                                                newTx.getId());
                                                        newTxS.setAmount(newTx.getCharges());
                                                        newTxS.setGateway_id(newTx.getGateway_id());

                                                        newTxS.setNarritive(
                                                                Transaction.TX_TYPE_PAYOUT_REVENUE);
                                                        newTxS.setTransactions_log_id(
                                                                newTx.getId());
                                                        newTxS.setMerchant_id(
                                                                revenue_stock_account.getId());
                                                        newTxS.setDescription(
                                                                newTx.getTx_description());
                                                        newTxS.setRecorded_by("SYSTEM");
                                                        newTxS.setTx_type("CR");
                                                        res_string =
                                                                Common.recordStatementTx(
                                                                        newTxS,
                                                                        balance_type,
                                                                        jdbcTemplate,
                                                                        transactionManager);
                                                        if (!res_string.equals("success")) {
                                                            return res_string;
                                                        }
                                                    } // end if (charges > 0)
                                                } // end if (!useMerchantCreds)
                                            }

                                            return "success";
                                        } catch (Exception e) {
                                            Logger.getLogger(Common.class.getName())
                                                    .log(Level.SEVERE, e.getMessage(), e);
                                            Logger.getLogger(
                                                            AuthenticationController.class
                                                                    .getName())
                                                    .log(
                                                            Level.SEVERE,
                                                            "INTERNAL ERROR - SAVING TX UPDATE: "
                                                                    + e.getStackTrace(),
                                                            "");

                                            // transactionManager.rollback(status);
                                            status.setRollbackOnly();
                                            return GeneralException.getError(
                                                    "102", GeneralException.ERRORS_102);
                                        }
                                    }
                                });

                if (result.equals("success")) {

                    pResponse.setOurUniqueTxId(newTx.getTx_unique_id());
                    pResponse.setSafaricomRequestReference(newTx.getSafaricomRequestReference());
                    return GeneralSuccessResponse.getApiTxMessage(
                            "000", GeneralSuccessResponse.SUCCESS_000, pResponse);
                } else {
                    return result;
                }

            } else {
                GateWayResponse pResponse_ = new GateWayResponse();
                pResponse_.setHttpStatus("0");
                pResponse_.setStatus("ERROR");
                pResponse_.setRequestTrace("");
                pResponse_.setTransactionStatus("UNDETERMINED");
                pResponse_.setNetworkId("");
                pResponse_.setMessage(GeneralException.ERRORS_102);
                return GeneralException.getApiTxMessage(
                        "102", GeneralException.ERRORS_102, pResponse_);
            }

        } else {
            return result;
        }
    }
}
