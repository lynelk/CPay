package net.citotech.cito.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import net.citotech.cito.Common;
import net.citotech.cito.Model.Transaction;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TransactionRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TransactionRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Transaction> findByMerchantReference(long merchantId, String reference) {
        String sql = "SELECT * FROM `" + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG + "` "
                + "WHERE merchant_id=:merchant_id AND tx_merchant_ref=:tx_merchant_ref "
                + "ORDER BY id DESC LIMIT 1";
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("merchant_id", merchantId);
        parameters.addValue("tx_merchant_ref", reference);
        List<Transaction> transactions = jdbcTemplate.query(sql, parameters, new TransactionRowMapper());
        return transactions.isEmpty() ? Optional.empty() : Optional.of(transactions.get(0));
    }

    private static class TransactionRowMapper implements RowMapper<Transaction> {
        @Override
        public Transaction mapRow(ResultSet rs, int rowNum) throws SQLException {
            Transaction transaction = new Transaction();
            transaction.setId(rs.getLong("id"));
            transaction.setMerchant_id(rs.getString("merchant_id"));
            transaction.setGateway_id(rs.getString("gateway_id"));
            transaction.setOriginal_amount(rs.getDouble("original_amount"));
            transaction.setCharges(rs.getDouble("charges"));
            transaction.setStatus(rs.getString("status"));
            transaction.setCharging_method(rs.getString("charging_method"));
            transaction.setTx_request_trace(rs.getString("tx_request_trace"));
            transaction.setTx_update_trace(rs.getString("tx_update_trace"));
            transaction.setTx_description(rs.getString("tx_description"));
            transaction.setTx_merchant_description(rs.getString("tx_merchant_description"));
            transaction.setTx_unique_id(rs.getString("tx_unique_id"));
            transaction.setTx_gateway_ref(rs.getString("tx_gateway_ref"));
            transaction.setTx_merchant_ref(rs.getString("tx_merchant_ref"));
            transaction.setCreated_on(rs.getString("created_on"));
            transaction.setUpdated_on(rs.getString("updated_on"));
            transaction.setPayer_number(rs.getString("payer_number"));
            transaction.setTx_type(rs.getString("tx_type"));
            transaction.setTx_cost(rs.getDouble("tx_cost"));
            transaction.setCallback_url(rs.getString("callback_url"));
            return transaction;
        }
    }
}

