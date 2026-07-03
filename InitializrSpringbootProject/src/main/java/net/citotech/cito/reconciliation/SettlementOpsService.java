package net.citotech.cito.reconciliation;

import java.math.BigDecimal;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SettlementOpsService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SettlementOpsService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void openBatch(String reference, String providerCode, String channelCode, String currency, BigDecimal expectedAmount, String openedBy) {
        String sql = "INSERT INTO reconciliation_settlement_batches (batch_reference, provider_code, channel_code, currency, expected_amount, opened_by) VALUES (:reference, :provider_code, :channel_code, :currency, :expected_amount, :opened_by)";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("reference", reference);
        p.addValue("provider_code", providerCode);
        p.addValue("channel_code", channelCode);
        p.addValue("currency", currency);
        p.addValue("expected_amount", expectedAmount);
        p.addValue("opened_by", openedBy);
        jdbcTemplate.update(sql, p);
    }

    public int flagRecord(long recordId, String category, String batchReference) {
        String sql = "UPDATE reconciliation_records SET exception_category=:category, settlement_batch=:batch_reference WHERE id=:id";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("id", recordId);
        p.addValue("category", category);
        p.addValue("batch_reference", batchReference);
        return jdbcTemplate.update(sql, p);
    }

    public int closeBatch(String reference, String closedBy) {
        String sql = "UPDATE reconciliation_settlement_batches SET batch_status='CLOSED', closed_by=:closed_by, closed_at=CURRENT_TIMESTAMP WHERE batch_reference=:reference";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("reference", reference);
        p.addValue("closed_by", closedBy);
        return jdbcTemplate.update(sql, p);
    }
}
