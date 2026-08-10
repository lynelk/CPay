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

/** Legacy statement/balance mutation engine extracted from Common; public Common methods remain compatibility delegates. */
public final class LegacyStatementEngine {
    private LegacyStatementEngine() {}

public static String recordStatementTx(
            Statement tx,
            String balance_type,
            NamedParameterJdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(
                new TransactionCallback<String>() {
                    @Override
                    public String doInTransaction(TransactionStatus status) {
                        return recordStatementTxCore(tx, balance_type, jdbcTemplate, status);
                    }
                });
    }

public static String recordStatementTxWithoutTransaction(
            Statement tx,
            String balance_type,
            NamedParameterJdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            TransactionStatus status) {
        return recordStatementTxCore(tx, balance_type, jdbcTemplate, status);
    }

private static String recordStatementTxCore(
            Statement tx,
            String balance_type,
            NamedParameterJdbcTemplate jdbcTemplate,
            TransactionStatus status) {
        try {
            String normalizedBalanceType = balance_type == null ? "" : balance_type.trim();
            GatewayBalanceType statementBalanceType =
                    resolveStatementBalanceType(tx, normalizedBalanceType);
            if (statementBalanceType == null) {
                status.setRollbackOnly();
                return GeneralException.getError(
                        "102", GeneralException.ERRORS_102 + " " + balance_type);
            }

            // Balance query
            String balanceSql =
                    "SELECT * FROM "
                            + Common.DB_TABLE_MERCHANT_STATEMENT
                            + " WHERE merchant_id = :merchant_id "
                            + " ORDER BY id DESC LIMIT 1 "
                            + " FOR UPDATE";

            MapSqlParameterSource parametersBalanceSql = new MapSqlParameterSource();
            parametersBalanceSql.addValue("merchant_id", tx.getMerchant_id());

            // Now add the user to database
            String sql =
                    "INSERT INTO "
                            + Common.DB_TABLE_MERCHANT_STATEMENT
                            + " "
                            + " SET `merchant_id`=:merchant_id,"
                            + " `gateway_id`=:gateway_id, "
                            + " `description`=:description,"
                            + " `recorded_by`=:recorded_by,"
                            + " `amount`=:amount,"
                            + " `currency`=:currency,"
                            + " `tx_type`=:tx_type,"
                            + " `narrative`=:narrative,"
                            + " `airtelmm_balance`=:airtelmm_balance,"
                            + " `safaricom_balance`=:safaricom_balance,"
                            + " `sms_balance`=:sms_balance,"
                            + " `mtnmm_balance`=:mtnmm_balance";

            MapSqlParameterSource parameters = new MapSqlParameterSource();
            if (tx.getTransactions_log_id() > 0) {
                sql += ", transactions_log_id=:transactions_log_id ";
                parameters.addValue("transactions_log_id", tx.getTransactions_log_id());
            }
            parameters.addValue("merchant_id", tx.getMerchant_id());
            parameters.addValue("gateway_id", tx.getGateway_id());
            parameters.addValue("description", tx.getDescription());
            parameters.addValue("amount", tx.getAmount());
            parameters.addValue("currency", statementBalanceType.currencyCode());
            parameters.addValue("tx_type", tx.getTx_type());
            parameters.addValue("narrative", tx.getNarritive());
            parameters.addValue("recorded_by", tx.getRecorded_by());

            final String sql_final = sql;

            RowMapper<Statement> rm_b =
                    new RowMapper<Statement>() {
                        public Statement mapRow(ResultSet rs, int rowNum) throws SQLException {
                            Statement t = new Statement();
                            t.setId(rs.getLong("id"));
                            t.setAmount(rs.getDouble("amount"));
                            t.setAirtelmm_balance(rs.getDouble("airtelmm_balance"));
                            t.setMtnmm_balance(rs.getDouble("mtnmm_balance"));
                            t.setSafaricom_balance(rs.getDouble("safaricom_balance"));
                            t.setCreated_on(rs.getString("created_on"));
                            t.setUpdated_on(rs.getString("updated_on"));
                            t.setGateway_id(rs.getString("gateway_id"));
                            t.setDescription(rs.getString("description"));
                            t.setMerchant_id(rs.getLong("merchant_id"));
                            t.setNarritive(rs.getString("narrative"));
                            t.setTransactions_log_id(rs.getLong("transactions_log_id"));
                            t.setTx_type(rs.getString("tx_type"));
                            t.setSms_balance(rs.getDouble("sms_balance"));
                            return t;
                        }
                    };

            List<Statement> balanceList =
                    jdbcTemplate.query(balanceSql, parametersBalanceSql, rm_b);
            Balance mtn_balance;
            Balance airtel_balance;
            Balance sms_balance;
            Balance safaricom_balance;

            if (balanceList.size() > 0) {
                Statement s = balanceList.get(0);
                mtn_balance =
                        new Balance(
                                GatewayBalanceType.MTN_MOMO.label(),
                                s.getMtnmm_balance(),
                                GatewayBalanceType.MTN_MOMO.gatewayId());
                mtn_balance.setBaseCurrency(GatewayBalanceType.MTN_MOMO.currencyCode());

                airtel_balance =
                        new Balance(
                                GatewayBalanceType.AIRTEL_MONEY.label(),
                                s.getAirtelmm_balance(),
                                GatewayBalanceType.AIRTEL_MONEY.gatewayId());
                airtel_balance.setBaseCurrency(GatewayBalanceType.AIRTEL_MONEY.currencyCode());

                safaricom_balance =
                        new Balance(
                                GatewayBalanceType.SAFARICOM_MPESA.label(),
                                s.getSafaricom_balance(),
                                GatewayBalanceType.SAFARICOM_MPESA.gatewayId());
                safaricom_balance.setBaseCurrency(
                        GatewayBalanceType.SAFARICOM_MPESA.currencyCode());

                sms_balance =
                        new Balance(
                                GatewayBalanceType.SMS.label(),
                                s.getSms_balance(),
                                GatewayBalanceType.SMS.gatewayId());
                sms_balance.setBaseCurrency(GatewayBalanceType.SMS.currencyCode());
            } else {
                mtn_balance =
                        new Balance(
                                GatewayBalanceType.MTN_MOMO.label(),
                                0.00,
                                GatewayBalanceType.MTN_MOMO.gatewayId());
                mtn_balance.setBaseCurrency(GatewayBalanceType.MTN_MOMO.currencyCode());

                airtel_balance =
                        new Balance(
                                GatewayBalanceType.AIRTEL_MONEY.label(),
                                0.00,
                                GatewayBalanceType.AIRTEL_MONEY.gatewayId());
                airtel_balance.setBaseCurrency(GatewayBalanceType.AIRTEL_MONEY.currencyCode());

                safaricom_balance =
                        new Balance(
                                GatewayBalanceType.SAFARICOM_MPESA.label(),
                                0.00,
                                GatewayBalanceType.SAFARICOM_MPESA.gatewayId());
                safaricom_balance.setBaseCurrency(
                        GatewayBalanceType.SAFARICOM_MPESA.currencyCode());

                sms_balance =
                        new Balance(
                                GatewayBalanceType.SMS.label(),
                                0.00,
                                GatewayBalanceType.SMS.gatewayId());
                sms_balance.setBaseCurrency(GatewayBalanceType.SMS.currencyCode());
            }

            // New balance
            if (tx.getTx_type().contains("CR")) {
                if (normalizedBalanceType.equals("mtnmm_balance")) {
                    Double nBalance = tx.getAmount() + mtn_balance.getAmount();
                    parameters.addValue("mtnmm_balance", nBalance);
                    parameters.addValue("airtelmm_balance", airtel_balance.getAmount());
                    parameters.addValue("sms_balance", sms_balance.getAmount());
                    parameters.addValue("safaricom_balance", safaricom_balance.getAmount());
                }
                if (normalizedBalanceType.equals("airtelmm_balance")) {
                    Double nBalance = tx.getAmount() + airtel_balance.getAmount();
                    parameters.addValue("mtnmm_balance", mtn_balance.getAmount());
                    parameters.addValue("airtelmm_balance", nBalance);
                    parameters.addValue("sms_balance", sms_balance.getAmount());
                    parameters.addValue("safaricom_balance", safaricom_balance.getAmount());
                }
                if (normalizedBalanceType.equals("safaricom_balance")) {
                    Double nBalance = tx.getAmount() + safaricom_balance.getAmount();
                    parameters.addValue("mtnmm_balance", mtn_balance.getAmount());
                    parameters.addValue("airtelmm_balance", airtel_balance.getAmount());
                    parameters.addValue("sms_balance", sms_balance.getAmount());
                    parameters.addValue("safaricom_balance", nBalance);
                }
                if (normalizedBalanceType.equals("sms_balance")) {
                    Double nBalance = tx.getAmount() + sms_balance.getAmount();
                    parameters.addValue("mtnmm_balance", mtn_balance.getAmount());
                    parameters.addValue("airtelmm_balance", airtel_balance.getAmount());
                    parameters.addValue("sms_balance", nBalance);
                    parameters.addValue("safaricom_balance", safaricom_balance.getAmount());
                }
            } else {
                if (normalizedBalanceType.equals("mtnmm_balance")) {
                    // Check if there is enough balance for this transaction
                    if (tx.getAmount() > mtn_balance.getAmount()) {
                        status.setRollbackOnly();
                        return GeneralException.getError(
                                "111",
                                String.format(
                                        GeneralException.ERRORS_111,
                                        mtn_balance.getAmount(),
                                        mtn_balance.getCode()));
                    }
                    Double nBalance = mtn_balance.getAmount() - tx.getAmount();
                    parameters.addValue("mtnmm_balance", nBalance);
                    parameters.addValue("airtelmm_balance", airtel_balance.getAmount());
                    parameters.addValue("sms_balance", sms_balance.getAmount());
                    parameters.addValue("safaricom_balance", safaricom_balance.getAmount());
                }

                if (normalizedBalanceType.equals("airtelmm_balance")) {
                    if (tx.getAmount() > airtel_balance.getAmount()) {
                        status.setRollbackOnly();
                        return GeneralException.getError(
                                "111",
                                String.format(
                                        GeneralException.ERRORS_111,
                                        airtel_balance.getAmount(),
                                        airtel_balance.getCode()));
                    }
                    Double nBalance = airtel_balance.getAmount() - tx.getAmount();
                    parameters.addValue("mtnmm_balance", mtn_balance.getAmount());
                    parameters.addValue("airtelmm_balance", nBalance);
                    parameters.addValue("sms_balance", sms_balance.getAmount());
                    parameters.addValue("safaricom_balance", safaricom_balance.getAmount());
                }

                if (normalizedBalanceType.equals("sms_balance")) {
                    if (tx.getAmount() > sms_balance.getAmount()) {
                        status.setRollbackOnly();
                        return GeneralException.getError(
                                "111",
                                String.format(
                                        GeneralException.ERRORS_111,
                                        sms_balance.getAmount(),
                                        sms_balance.getCode()));
                    }
                    Double nBalance = sms_balance.getAmount() - tx.getAmount();
                    parameters.addValue("mtnmm_balance", mtn_balance.getAmount());
                    parameters.addValue("airtelmm_balance", airtel_balance.getAmount());
                    parameters.addValue("sms_balance", nBalance);
                    parameters.addValue("safaricom_balance", safaricom_balance.getAmount());
                }
                if (normalizedBalanceType.equals("safaricom_balance")) {
                    if (tx.getAmount() > safaricom_balance.getAmount()) {
                        status.setRollbackOnly();
                        return GeneralException.getError(
                                "111",
                                String.format(
                                        GeneralException.ERRORS_111,
                                        safaricom_balance.getAmount(),
                                        safaricom_balance.getCode()));
                    }
                    Double nBalance = safaricom_balance.getAmount() - tx.getAmount();
                    parameters.addValue("mtnmm_balance", mtn_balance.getAmount());
                    parameters.addValue("airtelmm_balance", airtel_balance.getAmount());
                    parameters.addValue("sms_balance", sms_balance.getAmount());
                    parameters.addValue("safaricom_balance", nBalance);
                }
                // More balances
            }

            if (!parameters.hasValue(statementBalanceType.columnName())) {
                status.setRollbackOnly();
                return GeneralException.getError(
                        "102", GeneralException.ERRORS_102 + " " + balance_type);
            }

            KeyHolder keyHolder = new GeneratedKeyHolder();
            // long userId;
            jdbcTemplate.update(sql_final, parameters, keyHolder);
            // Now insert privileges
            BigInteger statementId = (BigInteger) keyHolder.getKey();
            refreshMerchantChannelBalanceReadModel(
                    tx, statementBalanceType, parameters, jdbcTemplate);

            return "success";
        } catch (Exception e) {
            Logger.getLogger(Common.class.getName()).log(Level.SEVERE, e.getMessage(), e);
            status.setRollbackOnly();
            return GeneralException.getError("102", GeneralException.ERRORS_102);
        }
    }

private static GatewayBalanceType resolveStatementBalanceType(
            Statement tx, String balanceTypeColumn) {
        GatewayBalanceType gatewayType =
                tx == null ? null : GatewayBalanceType.fromGatewayId(tx.getGateway_id());
        if (gatewayType != null && gatewayType.columnName().equals(balanceTypeColumn)) {
            return gatewayType;
        }
        return GatewayBalanceType.fromColumnName(balanceTypeColumn);
    }

private static void refreshMerchantChannelBalanceReadModel(
            Statement tx,
            GatewayBalanceType balanceType,
            MapSqlParameterSource statementParameters,
            NamedParameterJdbcTemplate jdbcTemplate) {
        Object value = statementParameters.getValue(balanceType.columnName());
        BigDecimal balance = decimal(value);
        String sql =
                "INSERT INTO merchant_channel_balances "
                        + "(merchant_id, channel_code, gateway_id, currency, available_balance, ledger_balance, pending_balance) "
                        + "VALUES (:merchant_id, :channel_code, :gateway_id, :currency, :available_balance, :ledger_balance, 0) "
                        + "ON DUPLICATE KEY UPDATE gateway_id=:gateway_id, "
                        + "available_balance=:available_balance, ledger_balance=:ledger_balance";
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("merchant_id", tx.getMerchant_id());
        parameters.addValue("channel_code", balanceType.channelCode());
        parameters.addValue("gateway_id", balanceType.gatewayId());
        parameters.addValue("currency", balanceType.currencyCode());
        parameters.addValue("available_balance", balance);
        parameters.addValue("ledger_balance", balance);
        jdbcTemplate.update(sql, parameters);
    }

private static BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.setScale(4, RoundingMode.HALF_UP);
        }
        if (value == null) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return new BigDecimal(String.valueOf(value)).setScale(4, RoundingMode.HALF_UP);
    }
}
