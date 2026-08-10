package net.citotech.cito.transactions;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import net.citotech.cito.Common;
import net.citotech.cito.GeneralException;
import net.citotech.cito.Model.AirtelMoneyPaymentGateway;
import net.citotech.cito.Model.Balance;
import net.citotech.cito.Model.Beneficiary;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.Model.MerchantSms;
import net.citotech.cito.Model.MerchantUser;
import net.citotech.cito.Model.MTNMoMoPaymentGateway;
import net.citotech.cito.Model.Payment;
import net.citotech.cito.Model.SafariComPaymentGateway;
import net.citotech.cito.Model.SmsGateway;
import net.citotech.cito.Model.Statement;
import net.citotech.cito.Model.Transaction;
import net.citotech.cito.security.ColumnAllowlist;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Read-only transaction, batch, SMS and statement queries extracted from
 * {@code TransactionsLogController}.
 *
 * <p>The service deliberately preserves legacy JSON envelopes and field names, including the
 * misspelled {@code updaed_on}. Authentication and permission checks remain controller concerns;
 * this class owns SQL construction, row mapping, merchant scoping and presentation assembly only.
 */
@Service
public class TransactionQueryService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TransactionQueryService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String adminTransactions(String requestBody) {
        try {
            JSONObject request = new JSONObject(requestBody);
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            StringBuilder sql =
                    new StringBuilder("SELECT * FROM ")
                            .append(Common.DB_TABLE_MERCHANT_TRANSACTION_LOG);
            appendSearch(sql, parameters, request.optJSONObject("searchingValue"), " WHERE ");
            sql.append(" ORDER BY id DESC");
            appendLimit(sql, request.optString("pageSize", ""));
            List<Transaction> transactions =
                    jdbcTemplate.query(sql.toString(), parameters, Common.getTransactionRowMapper());
            return transactionResponse(transactions, false, null).toString();
        } catch (JSONException | IllegalArgumentException ex) {
            return GeneralException.getError("102", GeneralException.ERRORS_102);
        }
    }

    public String merchantTransactions(String requestBody, MerchantUser merchantUser) {
        try {
            JSONObject request = new JSONObject(requestBody);
            JSONObject rules = request.getJSONObject("search_rules");
            MapSqlParameterSource parameters =
                    new MapSqlParameterSource("merchant_id", merchantUser.getMerchant_id());
            StringBuilder sql =
                    new StringBuilder("SELECT * FROM ")
                            .append(Common.DB_TABLE_MERCHANT_TRANSACTION_LOG)
                            .append(" WHERE merchant_id = :merchant_id");
            appendSearch(sql, parameters, request.optJSONObject("searchingValue"), " AND ");
            appendDateWindow(sql, parameters, rules, 3);
            appendExact(sql, parameters, "status", rules.optString("status", ""));
            appendExact(sql, parameters, "tx_type", rules.optString("tx_type", ""));
            sql.append(" ORDER BY id DESC");

            Long total =
                    jdbcTemplate.queryForObject(
                            "SELECT count(*) FROM "
                                    + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG
                                    + " WHERE merchant_id = :merchant_id",
                            new MapSqlParameterSource("merchant_id", merchantUser.getMerchant_id()),
                            Long.class);
            List<Transaction> transactions =
                    jdbcTemplate.query(sql.toString(), parameters, Common.getTransactionRowMapper());
            return transactionResponse(transactions, true, total == null ? 0L : total).toString();
        } catch (JSONException | IllegalArgumentException ex) {
            return GeneralException.getError("102", GeneralException.ERRORS_102);
        }
    }

    public String merchantPayments(String requestBody, MerchantUser merchantUser) {
        try {
            JSONObject request = new JSONObject(requestBody);
            int pageSize = request.getInt("pageSize");
            int currentPage = request.optInt("currentPage", 0);
            MapSqlParameterSource parameters =
                    new MapSqlParameterSource("merchant_id", merchantUser.getMerchant_id());
            StringBuilder sql =
                    new StringBuilder("SELECT * FROM ")
                            .append(Common.DB_TABLE_MERCHANT_BATCH_TRANSACTION_LOG)
                            .append(" WHERE merchant_id = :merchant_id");
            appendSearch(sql, parameters, request.optJSONObject("searchingValue"), " AND ");
            sql.append(" ORDER BY id DESC");
            if (pageSize != 0) {
                sql.append(" LIMIT :offset, :page_size");
                parameters.addValue("offset", currentPage * pageSize);
                parameters.addValue("page_size", pageSize);
            }

            String countSql =
                    "SELECT count(*) FROM "
                            + Common.DB_TABLE_MERCHANT_BATCH_TRANSACTION_LOG
                            + " WHERE merchant_id = :merchant_id";
            Long total =
                    jdbcTemplate.queryForObject(
                            countSql,
                            new MapSqlParameterSource("merchant_id", merchantUser.getMerchant_id()),
                            Long.class);
            List<Payment> payments = jdbcTemplate.query(sql.toString(), parameters, paymentMapper());

            JSONObject response = success();
            response.put("total", total == null ? 0L : total);
            JSONArray rows = new JSONArray();
            for (Payment payment : payments) {
                JSONObject row = new JSONObject();
                row.put("id", payment.getId());
                row.put("name", payment.getName());
                row.put("tx_description", payment.getDescription());
                row.put("batch_id", payment.getPaymentId());
                row.put("status", payment.getStatus());
                row.put("total_amount", payment.getTotal_amount());
                row.put("total_charges", payment.getTotal_charges());
                row.put("created_by", payment.getCreated_by());
                row.put("created_on", payment.getCreated_on());

                int paid = 0;
                JSONArray beneficiaries = new JSONArray();
                for (Beneficiary beneficiary : payment.getBeneficiaries()) {
                    Transaction transaction = beneficiary.getTransaction();
                    JSONObject item = new JSONObject();
                    item.put("name", beneficiary.getName());
                    item.put("amount", beneficiary.getAmount());
                    item.put("account", beneficiary.getAccount());
                    item.put("account_type", beneficiary.getAccount_type());
                    item.put("beneficiary_status", beneficiary.getStatus());
                    if (Transaction.BATCH_PAYMENT_PAID.equals(beneficiary.getStatus())) {
                        paid++;
                    }
                    item.put("merchant_number", merchantUser.getMerchant_number());
                    item.put("merchant_name", merchantUser.getMerchant_name());
                    appendTransactionFields(item, transaction, false);
                    beneficiaries.put(item);
                }
                row.put("total_beneficiaries", payment.getBeneficiaries().size());
                row.put("total_paid", paid);
                row.put("beneficiaries", beneficiaries);
                rows.put(row);
            }
            response.put("data", rows);
            return response.toString();
        } catch (JSONException | IllegalArgumentException ex) {
            return GeneralException.getError("102", GeneralException.ERRORS_102);
        }
    }

    public String merchantSms(String requestBody, MerchantUser merchantUser) {
        try {
            JSONObject request = new JSONObject(requestBody);
            JSONObject rules = request.getJSONObject("search_rules");
            MapSqlParameterSource parameters =
                    new MapSqlParameterSource("merchant_id", merchantUser.getMerchant_id());
            StringBuilder sql =
                    new StringBuilder("SELECT * FROM ")
                            .append(Common.DB_TABLE_MERCHANT_SMS)
                            .append(" WHERE merchant_id = :merchant_id");
            appendSearch(sql, parameters, request.optJSONObject("searchingValue"), " AND ");
            appendDateWindow(sql, parameters, rules, 3);
            appendExact(sql, parameters, "status", rules.optString("status", ""));
            sql.append(" ORDER BY id DESC");
            appendLimit(sql, request.optString("pageSize", ""));

            List<MerchantSms> messages = jdbcTemplate.query(sql.toString(), parameters, smsMapper());
            JSONObject response = success();
            response.put("balances", balancesArray(merchantUser.getMerchant_id()));
            JSONArray data = new JSONArray();
            for (MerchantSms sms : messages) {
                JSONObject row = new JSONObject();
                row.put("id", sms.getId());
                row.put("content", sms.getContent());
                row.put("merchant_id", sms.getMerchant_id());
                JSONArray recipients = new JSONArray();
                for (String recipient : sms.getRecipients().split(",")) {
                    JSONObject item = new JSONObject();
                    item.put("msisdn", recipient);
                    item.put("status", sms.getStatus());
                    item.put("delete", false);
                    recipients.put(item);
                }
                row.put("recipients", recipients);
                row.put("recipients_string", sms.getRecipients());
                row.put("status", sms.getStatus());
                row.put("charge", sms.getCharge());
                row.put("cost", sms.getCost());
                row.put("trace", sms.getTrace());
                row.put("gw_response", sms.getGw_response());
                row.put("created_on", sms.getCreated_on());
                row.put("total_recipients", sms.getTotal_recipients());
                row.put("send_time", sms.getSend_time());
                row.put("total_amount", sms.getTotal_amount());
                data.put(row);
            }
            response.put("data", data);
            return response.toString();
        } catch (JSONException | IllegalArgumentException ex) {
            return GeneralException.getError("102", GeneralException.ERRORS_102);
        }
    }

    public String adminMerchantStatement(String requestBody) {
        try {
            JSONObject request = new JSONObject(requestBody);
            long merchantId = request.getLong("merchant_id");
            return statementResponse(request, merchantId, false);
        } catch (JSONException | IllegalArgumentException ex) {
            return GeneralException.getError("102", GeneralException.ERRORS_102);
        }
    }

    public String ownMerchantStatement(String requestBody, MerchantUser merchantUser) {
        try {
            return statementResponse(new JSONObject(requestBody), merchantUser.getMerchant_id(), true);
        } catch (JSONException | IllegalArgumentException ex) {
            return GeneralException.getError("102", GeneralException.ERRORS_102);
        }
    }

    private String statementResponse(JSONObject request, long merchantId, boolean signedAmounts) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("merchant_id", merchantId);
        StringBuilder sql =
                new StringBuilder("SELECT s.*, (SELECT payer_number FROM ")
                        .append(Common.DB_TABLE_MERCHANT_TRANSACTION_LOG)
                        .append(" WHERE id=s.transactions_log_id LIMIT 1) AS payer_number FROM ")
                        .append(Common.DB_TABLE_MERCHANT_STATEMENT)
                        .append(" AS s WHERE merchant_id = :merchant_id");
        StringBuilder countSql =
                new StringBuilder("SELECT count(*) FROM ")
                        .append(Common.DB_TABLE_MERCHANT_STATEMENT)
                        .append(" WHERE merchant_id = :merchant_id");

        appendSearch(sql, parameters, request.optJSONObject("searchingValue"), " AND ");
        JSONObject rules = request.optJSONObject("search_rules");
        if (rules != null
                && !rules.optString("start_date", "").isEmpty()
                && !rules.optString("end_date", "").isEmpty()) {
            Timestamp start = Timestamp.valueOf(rules.getString("start_date") + " 00:00:00");
            Timestamp end = Timestamp.valueOf(rules.getString("end_date") + " 23:59:59");
            sql.append(" AND (created_on BETWEEN :start_date AND :end_date)");
            countSql.append(" AND (created_on BETWEEN :start_date AND :end_date)");
            parameters.addValue("start_date", start);
            parameters.addValue("end_date", end);
        } else if (signedAmounts) {
            LocalDateTime now = LocalDateTime.now();
            sql.append(" AND (created_on BETWEEN :start_date AND :end_date)");
            parameters.addValue("start_date", Timestamp.valueOf(now.minusMonths(4)));
            parameters.addValue("end_date", Timestamp.valueOf(now));
        }
        sql.append(" ORDER BY id DESC");
        if (!signedAmounts) {
            appendLimit(sql, request.optString("pageSize", ""));
        }

        List<Statement> statements = jdbcTemplate.query(sql.toString(), parameters, statementMapper());
        JSONObject response = success();
        if (signedAmounts) {
            Long total = jdbcTemplate.queryForObject(countSql.toString(), parameters, Long.class);
            response.put("total", total == null ? 0L : total);
        }
        JSONArray data = new JSONArray();
        for (Statement statement : statements) {
            JSONObject row = statementJson(statement, signedAmounts);
            data.put(row);
        }
        response.put("data", data);
        response.put("balances", balancesText(merchantId));
        return response.toString();
    }

    private JSONObject statementJson(Statement statement, boolean signedAmount) {
        JSONObject row = new JSONObject();
        row.put("id", statement.getId());
        row.put("gateway_id", statement.getGateway_id());
        row.put("merchant_id", statement.getMerchant_id());
        row.put("transactions_log_id", statement.getTransactions_log_id());
        row.put("created_on", statement.getCreated_on());
        row.put("updaed_on", statement.getUpdated_on());
        row.put("description", statement.getDescription());
        double amount = statement.getAmount();
        row.put("amount", signedAmount && !"CR".equals(statement.getTx_type()) ? -amount : amount);
        row.put("mtnmm_balance", statement.getMtnmm_balance());
        row.put("airtelmm_balance", statement.getAirtelmm_balance());
        row.put("safaricom_balance", statement.getSafaricom_balance());
        row.put("sms_balance", statement.getSms_balance());
        row.put("tx_type", statement.getTx_type());
        row.put("narrative", statement.getNarritive());
        row.put("payer_number", statement.getPayer_number());
        row.put("balances", statementBalancesText(statement));
        return row;
    }

    private String statementBalancesText(Statement statement) {
        return MTNMoMoPaymentGateway.getGatewayCurrencyCode()
                + " "
                + Common.numberFormat(statement.getMtnmm_balance())
                + " | AirtelMM "
                + Common.numberFormat(statement.getAirtelmm_balance())
                + " | "
                + SafariComPaymentGateway.getGatewayCurrencyCode()
                + " "
                + Common.numberFormat(statement.getSafaricom_balance())
                + " | "
                + SmsGateway.gateway_currency_code
                + " "
                + Common.numberFormat(statement.getSms_balance());
    }

    private String balancesText(long merchantId) {
        StringBuilder text = new StringBuilder();
        for (Balance balance : Common.getMerchantBalances(String.valueOf(merchantId), jdbcTemplate)) {
            if (text.length() > 0) {
                text.append(" | ");
            }
            text.append(balance.getCode()).append(" ").append(Common.numberFormat(balance.getAmount()));
        }
        return text.toString();
    }

    private JSONArray balancesArray(long merchantId) {
        JSONArray result = new JSONArray();
        for (Balance balance : Common.getMerchantBalances(String.valueOf(merchantId), jdbcTemplate)) {
            JSONObject json = new JSONObject();
            json.put("amount", balance.getAmount());
            String[] type = balance.getBalance_type();
            json.put("balance_type", type == null || type.length == 0 ? "" : type[0]);
            json.put("code", balance.getCode());
            json.put("gateway_id", balance.getGateway_id());
            result.put(json);
        }
        return result;
    }

    private List<Beneficiary> batchBeneficiaries(long batchId) {
        String sql =
                "SELECT b.batch_id, b.name AS beneficiary_name, b.account, "
                        + "b.amount AS beneficiary_amount, b.account_type, b.id AS beneficiary_long_id, "
                        + "b.status AS beneficiary_status, b.id AS benficiary_id, t.* FROM "
                        + Common.DB_TABLE_MERCHANT_BATCH_TRANSACTION_BENEFICIARIES
                        + " b LEFT JOIN "
                        + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG
                        + " t ON b.id=t.beneficiary_id WHERE b.batch_id=:batch_id";
        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("batch_id", batchId),
                (rs, rowNum) -> {
                    Beneficiary beneficiary = new Beneficiary();
                    beneficiary.setId(rs.getLong("beneficiary_long_id"));
                    beneficiary.setName(rs.getString("beneficiary_name"));
                    beneficiary.setStatus(rs.getString("beneficiary_status"));
                    beneficiary.setAccount(rs.getString("account"));
                    beneficiary.setAmount(rs.getDouble("beneficiary_amount"));
                    beneficiary.setAccount_type(rs.getString("account_type"));
                    beneficiary.setTransaction(
                            rs.getString("gateway_id") == null
                                    ? null
                                    : Common.getTransactionRowMapper().mapRow(rs, rowNum));
                    return beneficiary;
                });
    }

    private RowMapper<Payment> paymentMapper() {
        return (rs, rowNum) -> {
            Payment payment = new Payment();
            payment.setId(rs.getLong("id"));
            payment.setName(rs.getString("name"));
            payment.setPaymentId(rs.getString("batch_id"));
            payment.setDescription(rs.getString("tx_description"));
            payment.setStatus(rs.getString("status"));
            payment.setCreated_on(rs.getString("created_on"));
            payment.setMerchant_id(rs.getLong("merchant_id"));
            payment.setTotal_amount(rs.getDouble("total_amount"));
            payment.setTotal_charges(rs.getDouble("total_charges"));
            payment.setCreated_by(rs.getString("created_by"));
            payment.setBeneficiaries(batchBeneficiaries(payment.getId()));
            return payment;
        };
    }

    private RowMapper<MerchantSms> smsMapper() {
        return (rs, rowNum) -> {
            MerchantSms sms = new MerchantSms();
            sms.setId(BigInteger.valueOf(rs.getLong("id")));
            sms.setContent(rs.getString("content"));
            sms.setGw_response(rs.getString("gw_response"));
            sms.setRecipients(rs.getString("recipients"));
            sms.setStatus(rs.getString("status"));
            sms.setCreated_on(rs.getString("created_on"));
            sms.setCharge(rs.getDouble("charge"));
            sms.setCost(rs.getDouble("cost"));
            sms.setTrace(rs.getString("trace"));
            sms.setTotal_recipients(rs.getInt("total_recipients"));
            sms.setMerchant_id(BigInteger.valueOf(rs.getLong("merchant_id")));
            sms.setTotal_amount(rs.getDouble("total_amount"));
            sms.setSend_time(rs.getString("send_time"));
            return sms;
        };
    }

    private RowMapper<Statement> statementMapper() {
        return (rs, rowNum) -> {
            Statement statement = new Statement();
            statement.setId(rs.getLong("id"));
            statement.setAmount(
                    BigDecimal.valueOf(rs.getDouble("amount"))
                            .setScale(2, RoundingMode.HALF_UP)
                            .doubleValue());
            statement.setTransactions_log_id(rs.getLong("transactions_log_id"));
            statement.setGateway_id(rs.getString("gateway_id"));
            statement.setCreated_on(rs.getString("created_on"));
            statement.setUpdated_on(rs.getString("updated_on"));
            statement.setAirtelmm_balance(rs.getDouble("airtelmm_balance"));
            statement.setMtnmm_balance(rs.getDouble("mtnmm_balance"));
            statement.setSafaricom_balance(rs.getDouble("safaricom_balance"));
            statement.setSms_balance(rs.getDouble("sms_balance"));
            statement.setDescription(rs.getString("description"));
            statement.setTx_type(rs.getString("tx_type"));
            statement.setNarritive(rs.getString("narrative"));
            statement.setPayer_number(rs.getString("payer_number"));
            return statement;
        };
    }

    private JSONObject transactionResponse(
            List<Transaction> transactions, boolean hideTraces, Long total) {
        JSONObject response = success();
        if (total != null) {
            response.put("total", total);
        }
        JSONArray data = new JSONArray();
        for (Transaction transaction : transactions) {
            data.put(transactionJson(transaction, hideTraces));
        }
        response.put("data", data);
        return response;
    }

    private JSONObject transactionJson(Transaction transaction, boolean hideTraces) {
        JSONObject json = new JSONObject();
        json.put("id", transaction.getId());
        Merchant merchant = Common.getMerchantById(transaction.getMerchant_id(), jdbcTemplate);
        json.put("merchant_number", merchant == null ? "" : merchant.getAccount_number());
        json.put("merchant_name", merchant == null ? "" : merchant.getName());
        appendTransactionFields(json, transaction, hideTraces);
        return json;
    }

    private void appendTransactionFields(
            JSONObject json, Transaction transaction, boolean hideTraces) {
        json.put("gateway_id", transaction == null ? "" : transaction.getGateway_id());
        json.put("charges", transaction == null ? "" : transaction.getCharges());
        json.put(
                "charges_formatted",
                transaction == null ? "" : Common.numberFormat(transaction.getCharges()));
        json.put("status", transaction == null ? "" : transaction.getStatus());
        json.put("original_amount", transaction == null ? "" : transaction.getOriginal_amount());
        json.put(
                "original_amount_formatted",
                transaction == null ? "" : Common.numberFormat(transaction.getOriginal_amount()));
        json.put("charging_method", transaction == null ? "" : transaction.getCharging_method());
        json.put("created_on", transaction == null ? "" : transaction.getCreated_on());
        json.put("updaed_on", transaction == null ? "" : transaction.getUpdated_on());
        json.put(
                "tx_request_trace",
                transaction == null || hideTraces ? "" : transaction.getTx_request_trace());
        json.put(
                "tx_update_trace",
                transaction == null || hideTraces ? "" : transaction.getTx_update_trace());
        json.put("tx_description", transaction == null ? "" : transaction.getTx_description());
        json.put(
                "tx_merchant_description",
                transaction == null ? "" : transaction.getTx_merchant_description());
        json.put("tx_unique_id", transaction == null ? "" : transaction.getTx_unique_id());
        json.put("tx_gateway_ref", transaction == null ? "" : transaction.getTx_gateway_ref());
        json.put("tx_merchant_ref", transaction == null ? "" : transaction.getTx_merchant_ref());
        json.put("payer_number", transaction == null ? "" : transaction.getPayer_number());
        json.put("tx_type", transaction == null ? "" : transaction.getTx_type());
        json.put("callback_trace", transaction == null ? "" : transaction.getCallback_trace());
    }

    private JSONObject success() {
        JSONObject response = new JSONObject();
        response.put("code", "000");
        response.put("message", "true");
        return response;
    }

    private void appendSearch(
            StringBuilder sql,
            MapSqlParameterSource parameters,
            JSONObject search,
            String conjunction) {
        if (search == null || search.isNull("category") || search.isNull("value")) {
            return;
        }
        String category = search.optString("category", "");
        String value = search.optString("value", "");
        if (category.isEmpty() || "all".equals(category) || value.isEmpty()) {
            return;
        }
        String safeCategory = ColumnAllowlist.validate(category);
        sql.append(conjunction).append(safeCategory).append(" LIKE :").append(safeCategory);
        parameters.addValue(safeCategory, "%" + value + "%");
    }

    private void appendDateWindow(
            StringBuilder sql,
            MapSqlParameterSource parameters,
            JSONObject rules,
            int defaultMonths) {
        String startDate = rules.optString("start_date", "");
        String endDate = rules.optString("end_date", "");
        Timestamp start;
        Timestamp end;
        if (!startDate.isEmpty() && !endDate.isEmpty()) {
            start = Timestamp.valueOf(startDate + " 00:00:00");
            end = Timestamp.valueOf(endDate + " 23:59:59");
        } else {
            LocalDateTime now = LocalDateTime.now();
            start = Timestamp.valueOf(now.minusMonths(defaultMonths));
            end = Timestamp.valueOf(now);
        }
        sql.append(" AND (created_on BETWEEN :start_date AND :end_date)");
        parameters.addValue("start_date", start);
        parameters.addValue("end_date", end);
    }

    private void appendExact(
            StringBuilder sql,
            MapSqlParameterSource parameters,
            String column,
            String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        sql.append(" AND ").append(column).append(" = :").append(column);
        parameters.addValue(column, value);
    }

    private void appendLimit(StringBuilder sql, String rawPageSize) {
        if (rawPageSize == null || rawPageSize.isBlank()) {
            return;
        }
        int limit = Math.max(1, Math.min(Integer.parseInt(rawPageSize.trim()), 1000));
        sql.append(" LIMIT ").append(limit);
    }

    static RowMapper<Long> totalMapper() {
        return new RowMapper<Long>() {
            @Override
            public Long mapRow(ResultSet rs, int rowNum) throws SQLException {
                return rs.getLong("total");
            }
        };
    }
}
