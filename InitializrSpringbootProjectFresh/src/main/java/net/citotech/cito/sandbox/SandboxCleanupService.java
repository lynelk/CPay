package net.citotech.cito.sandbox;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Additional sandbox-owned state cleaned by a full merchant sandbox reset. */
@Service
public class SandboxCleanupService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SandboxCleanupService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Map<String, Object> resetFinancialSimulations(long merchantId) {
        MapSqlParameterSource p = new MapSqlParameterSource("merchantId", merchantId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(
                "sandboxRefundsDeleted",
                jdbcTemplate.update(
                        "DELETE FROM sandbox_refunds WHERE merchant_id=:merchantId", p));
        result.put(
                "sandboxBatchRunsDeleted",
                jdbcTemplate.update(
                        "DELETE FROM sandbox_batch_payout_runs WHERE merchant_id=:merchantId", p));
        result.put("productionDataTouched", false);
        return result;
    }
}
