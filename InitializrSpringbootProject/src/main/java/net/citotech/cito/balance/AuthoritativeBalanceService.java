package net.citotech.cito.balance;

import java.math.BigDecimal;
import java.util.List;
import net.citotech.cito.Common;
import net.citotech.cito.Model.Balance;
import net.citotech.cito.Model.Transaction;
import net.citotech.cito.gateway.PaymentChannelAdapter;
import net.citotech.cito.gateway.PaymentChannelRegistry;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthoritativeBalanceService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PaymentChannelRegistry channelRegistry;

    public AuthoritativeBalanceService(NamedParameterJdbcTemplate jdbcTemplate, PaymentChannelRegistry channelRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.channelRegistry = channelRegistry;
    }

    @Transactional
    public int backfillFromLegacy(String startedBy) {
        long runId = startRun(startedBy);
        int merchants = 0;
        int balances = 0;
        String sql = "SELECT id FROM " + Common.DB_TABLE_MERCHANTS;
        List<Long> merchantIds = jdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, rowNum) -> rs.getLong("id"));
        for (Long merchantId : merchantIds) {
            merchants++;
            List<Balance> legacy = Common.getMerchantBalances(String.valueOf(merchantId), jdbcTemplate);
            for (Balance balance : legacy) {
                String channelCode = channelForGateway(balance.getGateway_id());
                String currency = balance.getBaseCurrency() == null || balance.getBaseCurrency().trim().isEmpty() ? inferCurrency(channelCode) : balance.getBaseCurrency();
                upsertBalance(merchantId, channelCode, balance.getGateway_id(), currency, balance.getAmountDecimal(), balance.getAmountDecimal(), BigDecimal.ZERO);
                balances++;
            }
        }
        finishRun(runId, merchants, balances, "completed");
        return balances;
    }

    @Transactional
    public void syncMerchantFromLegacy(long merchantId) {
        List<Balance> legacy = Common.getMerchantBalances(String.valueOf(merchantId), jdbcTemplate);
        for (Balance balance : legacy) {
            String channelCode = channelForGateway(balance.getGateway_id());
            String currency = balance.getBaseCurrency() == null || balance.getBaseCurrency().trim().isEmpty() ? inferCurrency(channelCode) : balance.getBaseCurrency();
            upsertBalance(merchantId, channelCode, balance.getGateway_id(), currency, balance.getAmountDecimal(), balance.getAmountDecimal(), BigDecimal.ZERO);
        }
    }

    @Transactional
    public void recordTransactionEvent(Transaction tx, String sourceType, String eventType) {
        long merchantId = Long.parseLong(tx.getMerchant_id());
        String channelCode = channelForGateway(tx.getGateway_id());
        String currency = tx.getCurrency() == null || tx.getCurrency().trim().isEmpty() ? inferCurrency(channelCode) : tx.getCurrency();
        BigDecimal amount = tx.getOriginalAmountDecimal();
        BigDecimal pendingDelta = BigDecimal.ZERO;
        BigDecimal ledgerDelta = BigDecimal.ZERO;
        if (Transaction.TX_TYPE_PAYIN.equals(tx.getTx_type())) {
            pendingDelta = amount;
        } else if (Transaction.TX_TYPE_PAYOUT.equals(tx.getTx_type()) || Transaction.TX_TYPE_PAYOUT_REVERSAL.equals(tx.getTx_type())) {
            pendingDelta = amount.negate();
        }
        insertLedgerEvent(merchantId, channelCode, tx.getGateway_id(), currency, eventType, sourceType, tx.getTx_unique_id(), BigDecimal.ZERO, pendingDelta, ledgerDelta);
        applyDelta(merchantId, channelCode, tx.getGateway_id(), currency, BigDecimal.ZERO, ledgerDelta, pendingDelta);
    }

    private long startRun(String startedBy) {
        String sql = "INSERT INTO normalized_balance_backfill_runs (started_by) VALUES (:started_by)";
        MapSqlParameterSource p = new MapSqlParameterSource("started_by", startedBy == null ? "system" : startedBy);
        jdbcTemplate.update(sql, p);
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", new MapSqlParameterSource(), Long.class);
        return id != null ? id : 0L;
    }

    private void finishRun(long runId, int merchants, int balances, String message) {
        String sql = "UPDATE normalized_balance_backfill_runs SET run_status='DONE', finished_at=CURRENT_TIMESTAMP, merchants_processed=:merchants, balances_written=:balances, message=:message WHERE id=:id";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("id", runId);
        p.addValue("merchants", merchants);
        p.addValue("balances", balances);
        p.addValue("message", message);
        jdbcTemplate.update(sql, p);
    }

    private void upsertBalance(long merchantId, String channelCode, String gatewayId, String currency, BigDecimal available, BigDecimal ledger, BigDecimal pending) {
        String sql = "INSERT INTO merchant_channel_balances (merchant_id, channel_code, gateway_id, currency, available_balance, ledger_balance, pending_balance) VALUES (:merchant_id, :channel_code, :gateway_id, :currency, :available_balance, :ledger_balance, :pending_balance) ON DUPLICATE KEY UPDATE gateway_id=:gateway_id, available_balance=:available_balance, ledger_balance=:ledger_balance, pending_balance=:pending_balance";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchant_id", merchantId);
        p.addValue("channel_code", channelCode);
        p.addValue("gateway_id", gatewayId);
        p.addValue("currency", currency);
        p.addValue("available_balance", available);
        p.addValue("ledger_balance", ledger);
        p.addValue("pending_balance", pending);
        jdbcTemplate.update(sql, p);
    }

    private void applyDelta(long merchantId, String channelCode, String gatewayId, String currency, BigDecimal availableDelta, BigDecimal ledgerDelta, BigDecimal pendingDelta) {
        String sql = "INSERT INTO merchant_channel_balances (merchant_id, channel_code, gateway_id, currency, available_balance, ledger_balance, pending_balance) VALUES (:merchant_id, :channel_code, :gateway_id, :currency, :available_delta, :ledger_delta, :pending_delta) ON DUPLICATE KEY UPDATE gateway_id=:gateway_id, available_balance=available_balance+:available_delta, ledger_balance=ledger_balance+:ledger_delta, pending_balance=pending_balance+:pending_delta";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchant_id", merchantId);
        p.addValue("channel_code", channelCode);
        p.addValue("gateway_id", gatewayId);
        p.addValue("currency", currency);
        p.addValue("available_delta", availableDelta);
        p.addValue("ledger_delta", ledgerDelta);
        p.addValue("pending_delta", pendingDelta);
        jdbcTemplate.update(sql, p);
    }

    private void insertLedgerEvent(long merchantId, String channelCode, String gatewayId, String currency, String eventType, String sourceType, String sourceReference, BigDecimal amountDelta, BigDecimal pendingDelta, BigDecimal ledgerDelta) {
        String sql = "INSERT IGNORE INTO balance_ledger_events (merchant_id, channel_code, gateway_id, currency, event_type, source_type, source_reference, amount_delta, pending_delta, ledger_delta) VALUES (:merchant_id, :channel_code, :gateway_id, :currency, :event_type, :source_type, :source_reference, :amount_delta, :pending_delta, :ledger_delta)";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchant_id", merchantId);
        p.addValue("channel_code", channelCode);
        p.addValue("gateway_id", gatewayId);
        p.addValue("currency", currency);
        p.addValue("event_type", eventType);
        p.addValue("source_type", sourceType);
        p.addValue("source_reference", sourceReference);
        p.addValue("amount_delta", amountDelta);
        p.addValue("pending_delta", pendingDelta);
        p.addValue("ledger_delta", ledgerDelta);
        jdbcTemplate.update(sql, p);
    }

    private String channelForGateway(String gatewayId) {
        return channelRegistry.findByLegacyGatewayId(gatewayId).map(adapter -> adapter.channelCode()).orElse(gatewayId == null ? "unknown" : gatewayId);
    }

    private String inferCurrency(String channelCode) {
        return channelCode != null && channelCode.contains("safaricom") ? "KES" : "UGX";
    }
}
