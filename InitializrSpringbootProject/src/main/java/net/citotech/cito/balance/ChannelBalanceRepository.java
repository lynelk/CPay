package net.citotech.cito.balance;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ChannelBalanceRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ChannelBalanceRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ChannelBalance> findByMerchant(long merchantId) {
        String sql = "SELECT merchant_id, channel_code, gateway_id, currency, available_balance, ledger_balance, reserved_balance "
                + "FROM merchant_channel_balances WHERE merchant_id=:merchant_id ORDER BY channel_code, currency";
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("merchant_id", merchantId);
        return jdbcTemplate.query(sql, parameters, (rs, rowNum) -> {
            ChannelBalance balance = new ChannelBalance();
            balance.setMerchantId(rs.getLong("merchant_id"));
            balance.setChannelCode(rs.getString("channel_code"));
            balance.setGatewayId(rs.getString("gateway_id"));
            balance.setCurrency(rs.getString("currency"));
            balance.setAvailableBalance(rs.getBigDecimal("available_balance"));
            balance.setLedgerBalance(rs.getBigDecimal("ledger_balance"));
            balance.setPendingBalance(rs.getBigDecimal("reserved_balance"));
            return balance;
        });
    }

    public void upsert(long merchantId, String channelCode, String gatewayId, String currency,
                       BigDecimal availableBalance, BigDecimal ledgerBalance, BigDecimal pendingBalance) {
        String sql = "INSERT INTO merchant_channel_balances "
                + "(merchant_id, channel_code, gateway_id, currency, available_balance, ledger_balance, reserved_balance) "
                + "VALUES (:merchant_id, :channel_code, :gateway_id, :currency, :available_balance, :ledger_balance, :reserved_balance) "
                + "ON DUPLICATE KEY UPDATE gateway_id=:gateway_id, available_balance=:available_balance, "
                + "ledger_balance=:ledger_balance, reserved_balance=:reserved_balance";
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("merchant_id", merchantId);
        parameters.addValue("channel_code", channelCode);
        parameters.addValue("gateway_id", gatewayId);
        parameters.addValue("currency", currency);
        parameters.addValue("available_balance", availableBalance);
        parameters.addValue("ledger_balance", ledgerBalance);
        parameters.addValue("reserved_balance", pendingBalance);
        jdbcTemplate.update(sql, parameters);
    }
}
