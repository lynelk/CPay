package net.citotech.cito.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Covers audit F8: a genuinely intact chain must verify clean, a chain with a row whose stored
 * hash no longer matches its recomputed content must be flagged, and pre-chain historical rows
 * (entry_hash NULL, see V28) must be skipped rather than treated as breaks.
 */
class AuditChainVerificationServiceTest {

    @Test
    void anIntactChainVerifiesCleanly() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        String hash1 = AuditChainService.computeEntryHash(AuditChainService.GENESIS, "Jane", "jane@example.com", null, "Logged in");
        String hash2 = AuditChainService.computeEntryHash(hash1, "Bob", "bob@example.com", null, "Updated settings");
        when(jdbcTemplate.queryForList(contains("audit_trail"), any(MapSqlParameterSource.class)))
            .thenReturn(List.of(
                row(1L, "Jane", "jane@example.com", "Logged in", AuditChainService.GENESIS, hash1),
                row(2L, "Bob", "bob@example.com", "Updated settings", hash1, hash2)));

        AuditChainVerificationService service = new AuditChainVerificationService(jdbcTemplate);
        AuditChainVerificationService.Result result = service.verifyAuditTrail();

        assertThat(result.intact()).isTrue();
        assertThat(result.hashedRows()).isEqualTo(2);
        assertThat(result.brokenAtIds()).isEmpty();
    }

    @Test
    void aTamperedRowIsDetected() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        String hash1 = AuditChainService.computeEntryHash(AuditChainService.GENESIS, "Jane", "jane@example.com", null, "Logged in");
        when(jdbcTemplate.queryForList(contains("audit_trail"), any(MapSqlParameterSource.class)))
            .thenReturn(List.of(
                // Action text was altered after the fact - stored entry_hash no longer matches.
                row(1L, "Jane", "jane@example.com", "Logged in AS ADMIN", AuditChainService.GENESIS, hash1)));

        AuditChainVerificationService service = new AuditChainVerificationService(jdbcTemplate);
        AuditChainVerificationService.Result result = service.verifyAuditTrail();

        assertThat(result.intact()).isFalse();
        assertThat(result.brokenAtIds()).containsExactly(1L);
    }

    @Test
    void aBrokenLinkBetweenRowsIsDetectedEvenIfEachRowsOwnHashRecomputes() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        String hash1 = AuditChainService.computeEntryHash(AuditChainService.GENESIS, "Jane", "jane@example.com", null, "Logged in");
        // Row 2 claims a prev_hash that doesn't match row 1's actual entry_hash (e.g. row 1 was
        // deleted and a different row spliced in to hide it) - its own hash still recomputes fine
        // against ITS stored prev_hash, so only the link check catches this.
        String forgedPrevHash = "not-the-real-prior-hash";
        String hash2 = AuditChainService.computeEntryHash(forgedPrevHash, "Bob", "bob@example.com", null, "Updated settings");
        when(jdbcTemplate.queryForList(contains("audit_trail"), any(MapSqlParameterSource.class)))
            .thenReturn(List.of(
                row(1L, "Jane", "jane@example.com", "Logged in", AuditChainService.GENESIS, hash1),
                row(2L, "Bob", "bob@example.com", "Updated settings", forgedPrevHash, hash2)));

        AuditChainVerificationService service = new AuditChainVerificationService(jdbcTemplate);
        AuditChainVerificationService.Result result = service.verifyAuditTrail();

        assertThat(result.intact()).isFalse();
        assertThat(result.brokenAtIds()).containsExactly(2L);
    }

    @Test
    void preChainHistoricalRowsWithNoEntryHashAreSkippedNotFlagged() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        Map<String, Object> historicalRow = new LinkedHashMap<>();
        historicalRow.put("id", 1L);
        historicalRow.put("user_name", "Old User");
        historicalRow.put("user_id", "old@example.com");
        historicalRow.put("action", "Predates the hash chain");
        historicalRow.put("prev_hash", null);
        historicalRow.put("entry_hash", null);
        when(jdbcTemplate.queryForList(contains("audit_trail"), any(MapSqlParameterSource.class)))
            .thenReturn(List.of(historicalRow));

        AuditChainVerificationService service = new AuditChainVerificationService(jdbcTemplate);
        AuditChainVerificationService.Result result = service.verifyAuditTrail();

        assertThat(result.intact()).isTrue();
        assertThat(result.hashedRows()).isZero();
    }

    private Map<String, Object> row(long id, String userName, String userId, String action, String prevHash, String entryHash) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("user_name", userName);
        row.put("user_id", userId);
        row.put("action", action);
        row.put("prev_hash", prevHash);
        row.put("entry_hash", entryHash);
        return row;
    }
}
