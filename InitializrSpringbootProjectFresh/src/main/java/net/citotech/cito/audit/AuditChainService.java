package net.citotech.cito.audit;

import java.util.List;
import net.citotech.cito.security.CanonicalRequestSigner;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Hash-chain primitives shared by the audit_trail/merchants_audit_trail writers (Common.recordAction/
 * recordMerchantAction) and their verifier (AuditChainVerificationService) - audit F8. Each row's
 * entry_hash covers its own content plus the previous row's entry_hash, so altering or deleting any
 * row breaks every hash chained after it. Both the write side and the verify side call
 * {@link #computeEntryHash} with the exact same arguments so there is one place, not two, that
 * defines what a row's hash actually covers.
 */
public final class AuditChainService {
    public static final String GENESIS = "GENESIS";

    private AuditChainService() {
    }

    /** The chain's current tip for this table, or GENESIS if the chain hasn't started yet. */
    public static String fetchLastHash(String tableName, NamedParameterJdbcTemplate jdbcTemplate) {
        List<String> rows = jdbcTemplate.query(
            "SELECT entry_hash FROM " + tableName + " WHERE entry_hash IS NOT NULL ORDER BY id DESC LIMIT 1",
            new MapSqlParameterSource(),
            (rs, rowNum) -> rs.getString("entry_hash"));
        return rows.isEmpty() ? GENESIS : rows.get(0);
    }

    public static String computeEntryHash(String prevHash, String userName, String userId, String merchantId, String action) {
        String content = (prevHash == null ? GENESIS : prevHash)
            + "|" + nullToEmpty(userName)
            + "|" + nullToEmpty(userId)
            + "|" + nullToEmpty(merchantId)
            + "|" + nullToEmpty(action);
        return CanonicalRequestSigner.sha256Hex(content);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
