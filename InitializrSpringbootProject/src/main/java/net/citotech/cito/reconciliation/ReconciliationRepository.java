package net.citotech.cito.reconciliation;

import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReconciliationRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ReconciliationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ReconciliationRecord> findUnmatched(int limit) {
        String sql = "SELECT * FROM reconciliation_records WHERE match_status='UNMATCHED' ORDER BY id ASC LIMIT :limit";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("limit", limit), (rs, rowNum) -> {
            ReconciliationRecord record = new ReconciliationRecord();
            record.setId(rs.getLong("id"));
            record.setProviderCode(rs.getString("provider_code"));
            record.setChannelCode(rs.getString("channel_code"));
            record.setProviderReference(rs.getString("provider_reference"));
            record.setMerchantReference(rs.getString("merchant_reference"));
            record.setTransactionId(rs.getString("transaction_id"));
            record.setAmount(rs.getBigDecimal("amount"));
            record.setCurrency(rs.getString("currency"));
            record.setMatchStatus(rs.getString("match_status"));
            record.setMatchReason(rs.getString("match_reason"));
            return record;
        });
    }

    public int autoMatchByMerchantReference() {
        String sql = "UPDATE reconciliation_records rr "
                + "JOIN merchant_transactions_log tx ON tx.tx_merchant_ref = rr.merchant_reference "
                + "SET rr.transaction_id = tx.tx_unique_id, rr.match_status='MATCHED', rr.match_reason='merchant_reference' "
                + "WHERE rr.match_status='UNMATCHED' AND rr.merchant_reference IS NOT NULL";
        return jdbcTemplate.update(sql, new MapSqlParameterSource());
    }

    public void markManualMatch(long recordId, String transactionId, String reason) {
        String sql = "UPDATE reconciliation_records SET transaction_id=:transaction_id, match_status='MANUAL_MATCH', "
                + "match_reason=:match_reason WHERE id=:id";
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("id", recordId);
        parameters.addValue("transaction_id", transactionId);
        parameters.addValue("match_reason", reason);
        jdbcTemplate.update(sql, parameters);
    }
}
