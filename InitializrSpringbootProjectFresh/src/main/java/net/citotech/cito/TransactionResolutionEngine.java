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

/** Transaction status-resolution and settlement/reversal command engine extracted from Common. */
public final class TransactionResolutionEngine {
    private TransactionResolutionEngine() {}

private static boolean providerReferenceAlreadyApplied(
            Transaction tx, NamedParameterJdbcTemplate jdbcTemplate) {
        if (tx == null
                || tx.getId() == 0
                || tx.getGateway_id() == null
                || tx.getGateway_id().isBlank()
                || tx.getTx_gateway_ref() == null
                || tx.getTx_gateway_ref().isBlank()) {
            return false;
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("id", tx.getId());
        parameters.addValue("gateway_id", tx.getGateway_id());
        parameters.addValue("tx_gateway_ref", tx.getTx_gateway_ref());
        try {
            Integer count =
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM "
                                    + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG
                                    + " WHERE id<>:id AND gateway_id=:gateway_id "
                                    + "AND tx_gateway_ref=:tx_gateway_ref "
                                    + "AND status IN ('SUCCESSFUL','FAILED')",
                            parameters,
                            Integer.class);
            return count != null && count > 0;
        } catch (Exception ex) {
            Logger.getLogger(Common.class.getName())
                    .log(Level.WARNING, "Could not verify provider reference deduplication", ex);
            return false;
        }
    }

public static String updateTx(
            Transaction tx,
            NamedParameterJdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {

        boolean useMerchantCreds = Common.useMerchantProviderCredentials(jdbcTemplate);

        Merchant float_stock_account = null;
        Merchant revenue_stock_account = null;
        Merchant suspense_stock_account = null;

        if (!useMerchantCreds) {
            // Check stock|revenue|suspense accounts are configured
            Setting getStockAccount = Common.getSettings("float_stock_account", jdbcTemplate);
            if (getStockAccount == null || getStockAccount.getSetting_value().isEmpty()) {
                return GeneralException.getError("112", GeneralException.ERRORS_112);
            }

            Setting getRevenueAccount = Common.getSettings("revenue_account", jdbcTemplate);
            if (getRevenueAccount == null || getRevenueAccount.getSetting_value().isEmpty()) {
                return GeneralException.getError("117", GeneralException.ERRORS_117);
            }

            Setting getSuspenseAccount = Common.getSettings("suspense_account", jdbcTemplate);
            if (getSuspenseAccount == null || getSuspenseAccount.getSetting_value().isEmpty()) {
                return GeneralException.getError("127", GeneralException.ERRORS_127);
            }

            float_stock_account =
                    Common.getMerchantByAccountNumber(
                            getStockAccount.getSetting_value().trim(), jdbcTemplate);
            revenue_stock_account =
                    Common.getMerchantByAccountNumber(
                            getRevenueAccount.getSetting_value().trim(), jdbcTemplate);
            suspense_stock_account =
                    Common.getMerchantByAccountNumber(
                            getSuspenseAccount.getSetting_value().trim(), jdbcTemplate);
        }

        DoPayGateway gwChargingDetails = new DoPayGateway();

        String tx_type = "";
        if (tx.getTx_type().equals(Transaction.TX_TYPE_PAYIN)) {
            tx_type = "collection";
        } else {
            tx_type = "disbursement";
        }

        GateWayResponse txUpdatedDetails;
        if (!tx.isFinalStatusSet()) {
            txUpdatedDetails =
                    gwChargingDetails.runPayGatewayDoCheckStatus(
                            jdbcTemplate,
                            tx.getGateway_id(),
                            tx.getTx_unique_id(),
                            tx_type,
                            Long.parseLong(tx.getMerchant_id()));
        } else {
            txUpdatedDetails = new GateWayResponse();
            txUpdatedDetails.setTransactionStatus(tx.getStatus());
            txUpdatedDetails.setMessage("Updated from callback");
            txUpdatedDetails.setHttpStatus("200");
            txUpdatedDetails.setMessage("Updated from callback");
            txUpdatedDetails.setStatus("OK");
            txUpdatedDetails.setNetworkId(tx.getTx_gateway_ref());
            txUpdatedDetails.setRequestTrace(tx.getTx_update_trace());
        }

        String sql_update =
                " UPDATE "
                        + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG
                        + " "
                        + " SET status=:status, tx_update_trace=:tx_update_trace, "
                        + " tx_gateway_ref=:tx_gateway_ref ";

        if (txUpdatedDetails != null) {

            if (txUpdatedDetails.getTransactionStatus().isEmpty()) {
                Logger.getLogger(TransactionsLogController.class.getName())
                        .log(
                                Level.SEVERE,
                                "Empty Tx Status: " + txUpdatedDetails.getRequestTrace(),
                                "");
                return "success";
            }

            MapSqlParameterSource parameters_ = new MapSqlParameterSource();
            tx.setTx_update_trace(txUpdatedDetails.getRequestTrace());
            String previousStatusValue = tx.getStatus();
            tx.setStatus(txUpdatedDetails.getTransactionStatus());
            tx.setTx_gateway_ref(txUpdatedDetails.getNetworkId());

            // Explicit state-machine validation (audit B2): log when a provider/callback tries
            // to move a transaction out of a terminal state. The SQL guard below is what actually
            // enforces this (it's authoritative under concurrency); this is a named, testable
            // check on top of it rather than the DB WHERE clause being the only place the rule
            // "terminal states are final" is expressed.
            net.citotech.cito.Model.TransactionStatus previousStatus =
                    net.citotech.cito.Model.TransactionStatus.fromString(previousStatusValue);
            net.citotech.cito.Model.TransactionStatus nextStatus =
                    net.citotech.cito.Model.TransactionStatus.fromString(tx.getStatus());
            if (previousStatus != null
                    && nextStatus != null
                    && !previousStatus.canTransitionTo(nextStatus)) {
                Logger.getLogger(Common.class.getName())
                        .log(
                                Level.WARNING,
                                "Rejected invalid transaction status transition for tx id="
                                        + tx.getId()
                                        + ": "
                                        + previousStatus
                                        + " -> "
                                        + nextStatus
                                        + " (terminal statuses cannot transition again)",
                                "");
            }

            if (nextStatus != null
                    && nextStatus.isTerminal()
                    && providerReferenceAlreadyApplied(tx, jdbcTemplate)) {
                Logger.getLogger(Common.class.getName())
                        .log(
                                Level.WARNING,
                                "Ignoring duplicate provider status update for tx id="
                                        + tx.getId()
                                        + ", provider ref="
                                        + tx.getTx_gateway_ref()
                                        + " - another terminal transaction already used it");
                return "success";
            }

            // Guard against re-delivered provider callbacks: only transition rows that are
            // still in a non-terminal state. If 0 rows are affected, this status update has
            // already been applied (or the tx is otherwise terminal) - do not re-run the
            // ledger/statement sequence below, or a duplicate callback would double-credit.
            final String sql_update_final =
                    sql_update + " WHERE id=:id AND status NOT IN ('SUCCESSFUL','FAILED')";
            parameters_.addValue("id", tx.getId());
            parameters_.addValue("tx_update_trace", tx.getTx_update_trace());
            parameters_.addValue("status", tx.getStatus());
            parameters_.addValue("tx_gateway_ref", tx.getTx_gateway_ref());

            TransactionTemplate template = new TransactionTemplate(transactionManager);
            String result =
                    template.execute(
                            new TransactionCallback<String>() {
                                @Override
                                public String doInTransaction(TransactionStatus status) {
                                    try {
                                        int rowsUpdated =
                                                jdbcTemplate.update(sql_update_final, parameters_);
                                        return rowsUpdated > 0 ? "success" : "duplicate";
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

            if (result.equals("duplicate")) {
                Logger.getLogger(TransactionsLogController.class.getName())
                        .log(
                                Level.WARNING,
                                "Ignoring re-delivered status update for tx id="
                                        + tx.getId()
                                        + " - already in a terminal state, skipping ledger re-application",
                                "");
                // Acknowledge as success so the provider stops retrying, without re-applying
                // the statement/ledger writes a second time.
                return "success";
            }

            if (result.equals("success")) {
                Merchant merchant = Common.getMerchantById(tx.getMerchant_id(), jdbcTemplate);

                // If the transaction SUCCEEDED, then CREDIT THE CUSTOMER'S ACCOUNT
                if (txUpdatedDetails.getTransactionStatus().equals("SUCCESSFUL")) {

                    Common.enqueueMerchantCallback(tx, merchant, jdbcTemplate);

                    // Record this transaction
                    String[] bType = Balance.getBalanceTypeByGatewayId(tx.getGateway_id());
                    String balance_type = bType[0];

                    Statement newTx = new Statement();

                    // Record the charge and update stock and revenue account
                    if (tx.getTx_type().equals(Transaction.TX_TYPE_PAYIN)) {
                        // Credit this customer's account.
                        newTx.setTransactions_log_id(tx.getId());
                        newTx.setAmount(tx.getOriginal_amount());
                        newTx.setGateway_id(tx.getGateway_id());
                        newTx.setNarritive(tx.getTx_type());
                        newTx.setTransactions_log_id(tx.getId());
                        newTx.setMerchant_id(Long.parseLong(tx.getMerchant_id()));
                        newTx.setDescription(tx.getTx_description());
                        newTx.setRecorded_by("SYSTEM");
                        newTx.setTx_type("CR");

                        result =
                                Common.recordStatementTx(
                                        newTx, balance_type, jdbcTemplate, transactionManager);
                        if (!result.equals("success")) {
                            // release lock
                            return result;
                        }

                        if (tx.getCharges() > 0) {
                            newTx = new Statement();
                            newTx.setTransactions_log_id(tx.getId());
                            newTx.setAmount(tx.getCharges());
                            newTx.setGateway_id(tx.getGateway_id());

                            newTx.setNarritive(Transaction.TX_TYPE_PAYIN_CHARGE);
                            newTx.setTransactions_log_id(tx.getId());
                            newTx.setMerchant_id(merchant.getId());
                            newTx.setDescription(tx.getTx_description());
                            newTx.setRecorded_by("SYSTEM");
                            newTx.setTx_type("DR");

                            result =
                                    Common.recordStatementTx(
                                            newTx, balance_type, jdbcTemplate, transactionManager);
                            if (!result.equals("success")) {
                                return result;
                            }
                        } // end if (charges > 0)

                        if (!useMerchantCreds) {
                            // Now record this revenue account.
                            if (tx.getCharges() > 0) {
                                newTx = new Statement();
                                newTx.setAmount(tx.getCharges());
                                newTx.setGateway_id(tx.getGateway_id());

                                newTx.setNarritive(Transaction.TX_TYPE_PAYIN_REVENUE);
                                newTx.setTransactions_log_id(tx.getId());
                                newTx.setMerchant_id(revenue_stock_account.getId());
                                newTx.setDescription(tx.getTx_description());
                                newTx.setRecorded_by("SYSTEM");
                                newTx.setTx_type("CR");
                                result =
                                        Common.recordStatementTx(
                                                newTx,
                                                balance_type,
                                                jdbcTemplate,
                                                transactionManager);

                                if (!result.equals("success")) {
                                    return result;
                                }
                            } // end if (charges > 0)

                            // Now increase stock account.
                            newTx = new Statement();
                            newTx.setAmount(tx.getOriginal_amount());
                            newTx.setGateway_id(tx.getGateway_id());

                            newTx.setNarritive(Transaction.TX_TYPE_PAYIN);
                            newTx.setTransactions_log_id(tx.getId());
                            newTx.setMerchant_id(float_stock_account.getId());
                            newTx.setDescription(tx.getTx_description());
                            newTx.setRecorded_by("SYSTEM");
                            newTx.setTx_type("CR");
                            result =
                                    Common.recordStatementTx(
                                            newTx, balance_type, jdbcTemplate, transactionManager);

                            if (!result.equals("success")) {
                                return result;
                            }
                        } // end if (!useMerchantCreds)
                    } else if (tx.getTx_type().equals(Transaction.TX_TYPE_PAYOUT)) {
                        if (!useMerchantCreds) {
                            // Record a settlement transaction for Payout
                            newTx = new Statement();
                            newTx.setAmount(tx.getOriginal_amount());
                            newTx.setGateway_id(tx.getGateway_id());

                            newTx.setNarritive(Transaction.TX_TYPE_PAYOUT_SETTLEMENT);
                            newTx.setTransactions_log_id(tx.getId());
                            newTx.setMerchant_id(suspense_stock_account.getId());
                            newTx.setDescription(tx.getTx_description());
                            newTx.setRecorded_by("SYSTEM");
                            newTx.setTx_type("DR");
                            result =
                                    Common.recordStatementTx(
                                            newTx, balance_type, jdbcTemplate, transactionManager);

                            if (!result.equals("success")) {
                                return result;
                            }

                            if (tx.getCharges() > 0) {
                                // Record a settlement transaction for Payout charge
                                newTx = new Statement();
                                newTx.setAmount(tx.getCharges());
                                newTx.setGateway_id(tx.getGateway_id());

                                newTx.setNarritive(Transaction.TX_TYPE_PAYOUT_CHARGE_SETTLEMENT);
                                newTx.setTransactions_log_id(tx.getId());
                                newTx.setMerchant_id(suspense_stock_account.getId());
                                newTx.setDescription(tx.getTx_description());
                                newTx.setRecorded_by("SYSTEM");
                                newTx.setTx_type("DR");
                                result =
                                        Common.recordStatementTx(
                                                newTx,
                                                balance_type,
                                                jdbcTemplate,
                                                transactionManager);

                                if (!result.equals("success")) {
                                    return result;
                                }

                                // Record Revenue to revenue account
                                newTx = new Statement();
                                newTx.setAmount(tx.getCharges());
                                newTx.setGateway_id(tx.getGateway_id());

                                newTx.setNarritive(Transaction.TX_TYPE_PAYOUT_REVENUE);
                                newTx.setTransactions_log_id(tx.getId());
                                newTx.setMerchant_id(revenue_stock_account.getId());
                                newTx.setDescription(tx.getTx_description());
                                newTx.setRecorded_by("SYSTEM");
                                newTx.setTx_type("CR");
                                result =
                                        Common.recordStatementTx(
                                                newTx,
                                                balance_type,
                                                jdbcTemplate,
                                                transactionManager);

                                if (!result.equals("success")) {
                                    return result;
                                }
                            } // end if (charges > 0)
                        } // end if (!useMerchantCreds)
                    }
                } else if (txUpdatedDetails.getTransactionStatus().equals("FAILED")) {

                    Common.enqueueMerchantCallback(tx, merchant, jdbcTemplate);

                    // If it's a payout, reverse the money.
                    Statement newTx = new Statement();
                    String[] bType = Balance.getBalanceTypeByGatewayId(tx.getGateway_id());
                    String balance_type = bType[0];
                    if (tx.getTx_type().equals(Transaction.TX_TYPE_PAYOUT)) {
                        if (!useMerchantCreds) {
                            // Dr the amount on suspense
                            newTx = new Statement();
                            newTx.setAmount(tx.getOriginal_amount());
                            newTx.setGateway_id(tx.getGateway_id());

                            newTx.setNarritive(Transaction.TX_TYPE_PAYOUT_REVERSAL);
                            newTx.setTransactions_log_id(tx.getId());
                            newTx.setMerchant_id(suspense_stock_account.getId());
                            newTx.setDescription(tx.getTx_description());
                            newTx.setRecorded_by("SYSTEM");
                            newTx.setTx_type("DR");
                            result =
                                    Common.recordStatementTx(
                                            newTx, balance_type, jdbcTemplate, transactionManager);

                            if (!result.equals("success")) {
                                return result;
                            }

                            if (tx.getCharges() > 0) {
                                // DR the charge reversal on suspense
                                newTx = new Statement();
                                newTx.setAmount(tx.getCharges());
                                newTx.setGateway_id(tx.getGateway_id());

                                newTx.setNarritive(Transaction.TX_TYPE_PAYOUT_CHARGE_REVERSAL);
                                newTx.setTransactions_log_id(tx.getId());
                                newTx.setMerchant_id(suspense_stock_account.getId());
                                newTx.setDescription(tx.getTx_description());
                                newTx.setRecorded_by("SYSTEM");
                                newTx.setTx_type("DR");
                                result =
                                        Common.recordStatementTx(
                                                newTx,
                                                balance_type,
                                                jdbcTemplate,
                                                transactionManager);

                                if (!result.equals("success")) {
                                    return result;
                                }
                            } // end if (charges > 0)
                        } // end if (!useMerchantCreds)

                        // CR the amount back to customer's account
                        newTx = new Statement();
                        newTx.setAmount(tx.getOriginal_amount());
                        newTx.setGateway_id(tx.getGateway_id());

                        newTx.setNarritive(Transaction.TX_TYPE_PAYOUT_REVERSAL);
                        newTx.setTransactions_log_id(tx.getId());
                        newTx.setMerchant_id(merchant.getId());
                        newTx.setDescription(tx.getTx_description());
                        newTx.setRecorded_by("SYSTEM");
                        newTx.setTx_type("CR");
                        result =
                                Common.recordStatementTx(
                                        newTx, balance_type, jdbcTemplate, transactionManager);

                        if (!result.equals("success")) {
                            return result;
                        }

                        if (tx.getCharges() > 0) {
                            // CR the charge back on customer's account
                            newTx = new Statement();
                            newTx.setAmount(tx.getCharges());
                            newTx.setGateway_id(tx.getGateway_id());

                            newTx.setNarritive(Transaction.TX_TYPE_PAYOUT_CHARGE_REVERSAL);
                            newTx.setTransactions_log_id(tx.getId());
                            newTx.setMerchant_id(merchant.getId());
                            newTx.setDescription(tx.getTx_description());
                            newTx.setRecorded_by("SYSTEM");
                            newTx.setTx_type("CR");
                            result =
                                    Common.recordStatementTx(
                                            newTx, balance_type, jdbcTemplate, transactionManager);

                            if (!result.equals("success")) {
                                return result;
                            }
                        } // end if (charges > 0)

                        if (!useMerchantCreds) {
                            // Restore the float account
                            newTx = new Statement();
                            newTx.setAmount(tx.getOriginal_amount());
                            newTx.setGateway_id(tx.getGateway_id());

                            newTx.setNarritive(Transaction.TX_TYPE_PAYOUT_REVERSAL);
                            newTx.setTransactions_log_id(tx.getId());
                            newTx.setMerchant_id(float_stock_account.getId());
                            newTx.setDescription(tx.getTx_description());
                            newTx.setRecorded_by("SYSTEM");
                            newTx.setTx_type("CR");
                            result =
                                    Common.recordStatementTx(
                                            newTx, balance_type, jdbcTemplate, transactionManager);

                            if (!result.equals("success")) {
                                return result;
                            }
                        } // end if (!useMerchantCreds)
                    }
                }
                return result;
            } else {
                // release lock
                // lock.release();
                // close the file
                // writer.close();
                return result;
            }
        }
        return "error";
    }
}
