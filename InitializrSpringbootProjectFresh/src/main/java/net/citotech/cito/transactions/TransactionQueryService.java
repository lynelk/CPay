package net.citotech.cito.transactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import net.citotech.cito.Common;
import net.citotech.cito.GeneralException;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.Model.MerchantUser;
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
 * Read-only transaction listing/filtering extracted from TransactionsLogController.
 *
 * <p>The service deliberately preserves the legacy JSON envelope and field names, including the
 * misspelled {@code updaed_on}, because those are observable v1/portal compatibility contracts.
 * Authentication and permission checks remain HTTP/controller concerns.
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
            return response(transactions, false, null).toString();
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
            appendDateWindow(sql, parameters, rules);
            appendExact(sql, parameters, "status", rules.optString("status", ""));
            appendExact(sql, parameters, "tx_type", rules.optString("tx_type", ""));
            sql.append(" ORDER BY id DESC");

            String countSql =
                    "SELECT count(*) AS total FROM "
                            + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG
                            + " WHERE merchant_id = :merchant_id";
            Long total =
                    jdbcTemplate.queryForObject(
                            countSql,
                            new MapSqlParameterSource("merchant_id", merchantUser.getMerchant_id()),
                            Long.class);
            List<Transaction> transactions =
                    jdbcTemplate.query(sql.toString(), parameters, Common.getTransactionRowMapper());
            return response(transactions, true, total == null ? 0L : total).toString();
        } catch (JSONException | IllegalArgumentException ex) {
            return GeneralException.getError("102", GeneralException.ERRORS_102);
        }
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
            StringBuilder sql, MapSqlParameterSource parameters, JSONObject rules) {
        String startDate = rules.optString("start_date", "");
        String endDate = rules.optString("end_date", "");
        Timestamp start;
        Timestamp end;
        if (!startDate.isEmpty() && !endDate.isEmpty()) {
            try {
                start = Timestamp.valueOf(startDate + " 00:00:00");
                end = Timestamp.valueOf(endDate + " 23:59:59");
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("start_date/end_date must use YYYY-MM-DD", ex);
            }
        } else {
            LocalDateTime now = LocalDateTime.now();
            start = Timestamp.valueOf(now.minusMonths(3));
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

    private JSONObject response(List<Transaction> transactions, boolean hideTraces, Long total) {
        JSONObject response = new JSONObject();
        response.put("code", "000");
        response.put("message", "true");
        if (total != null) {
            response.put("total", total);
        }
        JSONArray data = new JSONArray();
        for (Transaction transaction : transactions) {
            data.put(toJson(transaction, hideTraces));
        }
        response.put("data", data);
        return response;
    }

    private JSONObject toJson(Transaction transaction, boolean hideTraces) {
        JSONObject json = new JSONObject();
        json.put("id", transaction.getId());
        Merchant merchant = Common.getMerchantById(transaction.getMerchant_id(), jdbcTemplate);
        json.put("merchant_number", merchant == null ? "" : merchant.getAccount_number());
        json.put("merchant_name", merchant == null ? "" : merchant.getName());
        json.put("gateway_id", transaction.getGateway_id());
        json.put("charges", transaction.getCharges());
        json.put("charges_formatted", Common.numberFormat(transaction.getCharges()));
        json.put("status", transaction.getStatus());
        json.put("original_amount", transaction.getOriginal_amount());
        json.put("original_amount_formatted", Common.numberFormat(transaction.getOriginal_amount()));
        json.put("charging_method", transaction.getCharging_method());
        json.put("created_on", transaction.getCreated_on());
        json.put("updaed_on", transaction.getUpdated_on());
        json.put("tx_request_trace", hideTraces ? "" : transaction.getTx_request_trace());
        json.put("tx_update_trace", hideTraces ? "" : transaction.getTx_update_trace());
        json.put("tx_description", transaction.getTx_description());
        json.put("tx_merchant_description", transaction.getTx_merchant_description());
        json.put("tx_unique_id", transaction.getTx_unique_id());
        json.put("tx_gateway_ref", transaction.getTx_gateway_ref());
        json.put("tx_merchant_ref", transaction.getTx_merchant_ref());
        json.put("payer_number", transaction.getPayer_number());
        json.put("tx_type", transaction.getTx_type());
        json.put("callback_trace", transaction.getCallback_trace());
        return json;
    }

    /** Small mapper retained for focused query tests that do not need the full Common mapper. */
    static RowMapper<Long> totalMapper() {
        return new RowMapper<Long>() {
            @Override
            public Long mapRow(ResultSet rs, int rowNum) throws SQLException {
                return rs.getLong("total");
            }
        };
    }
}
