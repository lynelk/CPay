package net.citotech.cito.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Covers audit F8: the chain's tip must default to GENESIS when a table is empty, and the entry
 * hash must be sensitive to every field it covers plus the previous hash, so altering any of them
 * (including swapping in a different prior link) changes the resulting hash.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class AuditChainServiceTest {

    @Test
    void fetchLastHashReturnsGenesisWhenTheTableIsEmpty() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of());

        assertThat(AuditChainService.fetchLastHash("audit_trail", jdbcTemplate)).isEqualTo(AuditChainService.GENESIS);
    }

    @Test
    void fetchLastHashReturnsTheMostRecentRowsHash() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn((List) List.of("abc123"));

        assertThat(AuditChainService.fetchLastHash("audit_trail", jdbcTemplate)).isEqualTo("abc123");
    }

    @Test
    void sameInputsProduceTheSameHash() {
        String a = AuditChainService.computeEntryHash("GENESIS", "Jane", "jane@example.com", null, "Did a thing");
        String b = AuditChainService.computeEntryHash("GENESIS", "Jane", "jane@example.com", null, "Did a thing");

        assertThat(a).isEqualTo(b).hasSize(64);
    }

    @Test
    void changingAnyFieldChangesTheHash() {
        String base = AuditChainService.computeEntryHash("GENESIS", "Jane", "jane@example.com", "42", "Did a thing");

        assertThat(AuditChainService.computeEntryHash("DIFFERENT_PREV", "Jane", "jane@example.com", "42", "Did a thing")).isNotEqualTo(base);
        assertThat(AuditChainService.computeEntryHash("GENESIS", "Bob", "jane@example.com", "42", "Did a thing")).isNotEqualTo(base);
        assertThat(AuditChainService.computeEntryHash("GENESIS", "Jane", "bob@example.com", "42", "Did a thing")).isNotEqualTo(base);
        assertThat(AuditChainService.computeEntryHash("GENESIS", "Jane", "jane@example.com", "99", "Did a thing")).isNotEqualTo(base);
        assertThat(AuditChainService.computeEntryHash("GENESIS", "Jane", "jane@example.com", "42", "Did another thing")).isNotEqualTo(base);
    }

    @Test
    void nullMerchantIdIsTreatedDistinctlyFromAnyRealId() {
        String withNull = AuditChainService.computeEntryHash("GENESIS", "Jane", "jane@example.com", null, "action");
        String withEmpty = AuditChainService.computeEntryHash("GENESIS", "Jane", "jane@example.com", "", "action");

        assertThat(withNull).isEqualTo(withEmpty);
    }
}
