package net.citotech.cito.balance;

import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BalanceViewRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BalanceViewRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<BalanceView> findByMerchant(long merchantId) {
        String sql = "SELECT merchant_id, channel_code, gateway_id, currency, available_balance, ledger_balance, pending_balance FROM merchant_channel_balances WHERE merchant_id=:merchant_id ORDER BY channel_code, currency";
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("merchant_id", merchantId);
        return jdbcTemplate.query(sql, parameters, (rs, rowNum) -> {
            BalanceView balance = new BalanceView();
            balance.merchantId = rs.getLong("merchant_id");
            balance.channelCode = rs.getString("channel_code");
            balance.gatewayId = rs.getString("gateway_id");
            balance.currency = rs.getString("currency");
            balance.availableBalance = rs.getBigDecimal("available_balance").toPlainString();
            balance.ledgerBalance = rs.getBigDecimal("ledger_balance").toPlainString();
            balance.pendingBalance = rs.getBigDecimal("pending_balance").toPlainString();
            return balance;
        });
    }
}

