package net.citotech.cito.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Covers audit F3: archiving must copy terminal rows and mark them without ever deleting from the
 * live table (deleting would trigger merchant_statement's ON DELETE SET NULL cascade, silently
 * severing old statement rows from their transaction reference data). Purging is a distinct
 * operation that must only ever delete rows that were already archived.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class TransactionLogArchivalServiceTest {

    @Test
    void archiveBatchCopiesAndMarksRowsWithoutIssuingAnyDelete() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(contains("archived_on IS NULL"), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of(10L, 11L));
        when(jdbcTemplate.update(contains("INSERT IGNORE INTO merchant_transactions_log_archive"), any(MapSqlParameterSource.class)))
            .thenReturn(2);
        when(jdbcTemplate.update(contains("SET archived_on=CURRENT_TIMESTAMP"), any(MapSqlParameterSource.class)))
            .thenReturn(2);
        TransactionLogArchivalService service = new TransactionLogArchivalService(jdbcTemplate);

        int archived = service.archiveBatch(365, 500);

        assertThat(archived).isEqualTo(2);
        verify(jdbcTemplate, never()).update(contains("DELETE FROM"), any(MapSqlParameterSource.class));
    }

    @Test
    void archiveBatchIsANoOpWhenNothingIsDue() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of());
        TransactionLogArchivalService service = new TransactionLogArchivalService(jdbcTemplate);

        assertThat(service.archiveBatch(365, 500)).isEqualTo(0);
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void purgeBatchOnlyEverSelectsRowsAlreadyArchived() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(contains("archived_on IS NOT NULL"), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of(5L));
        when(jdbcTemplate.update(contains("DELETE FROM merchant_transactions_log"), any(MapSqlParameterSource.class)))
            .thenReturn(1);
        TransactionLogArchivalService service = new TransactionLogArchivalService(jdbcTemplate);

        assertThat(service.purgeBatch(730, 500)).isEqualTo(1);
    }

    @Test
    void purgeBatchIsANoOpWhenNothingIsArchivedYet() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of());
        TransactionLogArchivalService service = new TransactionLogArchivalService(jdbcTemplate);

        assertThat(service.purgeBatch(730, 500)).isEqualTo(0);
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }
}
