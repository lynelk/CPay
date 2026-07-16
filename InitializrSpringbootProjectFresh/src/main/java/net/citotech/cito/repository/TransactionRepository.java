package net.citotech.cito.repository;

import java.util.List;
import java.util.Optional;
import net.citotech.cito.Common;
import net.citotech.cito.Model.Transaction;
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
        List<Transaction> transactions = jdbcTemplate.query(sql, parameters, Common.getTransactionRowMapper());
        return transactions.isEmpty() ? Optional.empty() : Optional.of(transactions.get(0));
    }
}

