package net.citotech.cito.audit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.citotech.cito.Common;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * On-demand tamper detection for the hash-chained audit_trail/merchants_audit_trail tables (audit
 * F8). Recomputes every hashed row's entry_hash from its stored content and compares it against
 * what's on disk, and confirms each row's prev_hash actually matches the row before it - either
 * check failing means a row was altered or removed after being written (the DB-level append-only
 * trigger from V28 should prevent that going forward; this is what proves it, and would also catch
 * any that happened before those triggers existed).
 */
@Service
public class AuditChainVerificationService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AuditChainVerificationService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Result verifyAuditTrail() {
        return verify(Common.DB_TABLE_AUDIT_TRAIL, false);
    }

    public Result verifyMerchantAuditTrail() {
        return verify(Common.DB_TABLE_AUDIT_TRAIL_MERCHANT, true);
    }

    private Result verify(String table, boolean hasMerchantId) {
        String sql = "SELECT id, user_name, user_id, action, prev_hash, entry_hash"
            + (hasMerchantId ? ", merchant_id" : "")
            + " FROM " + table + " ORDER BY id ASC";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, new MapSqlParameterSource());

        List<Long> brokenIds = new ArrayList<>();
        String expectedPrevHash = null;
        boolean chainStarted = false;
        int hashedRows = 0;

        for (Map<String, Object> row : rows) {
            String storedEntryHash = (String) row.get("entry_hash");
            if (storedEntryHash == null) {
                // Pre-chain historical row (see V28) - nothing to verify.
                continue;
            }
            hashedRows++;
            String storedPrevHash = (String) row.get("prev_hash");
            long id = ((Number) row.get("id")).longValue();

            if (!chainStarted) {
                chainStarted = true;
            } else if (!Objects.equals(storedPrevHash, expectedPrevHash)) {
                brokenIds.add(id);
            }

            String merchantId = hasMerchantId && row.get("merchant_id") != null
                ? String.valueOf(row.get("merchant_id")) : null;
            String recomputed = AuditChainService.computeEntryHash(
                storedPrevHash, (String) row.get("user_name"), (String) row.get("user_id"), merchantId, (String) row.get("action"));
            if (!recomputed.equals(storedEntryHash) && !brokenIds.contains(id)) {
                brokenIds.add(id);
            }
            expectedPrevHash = storedEntryHash;
        }
        return new Result(brokenIds.isEmpty(), hashedRows, brokenIds);
    }

    public record Result(boolean intact, int hashedRows, List<Long> brokenAtIds) {
    }
}
