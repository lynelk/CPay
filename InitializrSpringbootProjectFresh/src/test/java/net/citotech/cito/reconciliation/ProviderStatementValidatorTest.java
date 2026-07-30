package net.citotech.cito.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class ProviderStatementValidatorTest {

    @Test
    void countsValidInvalidAndDuplicateRowsAndPersistsAPassedRun() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(1L);
        ProviderStatementValidator validator = new ProviderStatementValidator(new ProviderStatementParserRegistry(), jdbcTemplate);
        String csv = "provider_reference,amount,currency\nPR-1,100,UGX\nPR-2,200,UGX\n";

        validator.validate("MTN", "statement.csv", csv.getBytes(StandardCharsets.UTF_8));

        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(contains("provider_statement_validation_runs"), captor.capture());
        assertThat(captor.getValue().getValue("status")).isEqualTo("PASSED");
        assertThat(captor.getValue().getValue("valid")).isEqualTo(2);
        assertThat(captor.getValue().getValue("invalid")).isEqualTo(0);
        assertThat(captor.getValue().getValue("duplicates")).isEqualTo(0);
    }

    @Test
    void aRowMissingRequiredFieldsIsCountedAsInvalidAndTheRunFails() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(1L);
        ProviderStatementValidator validator = new ProviderStatementValidator(new ProviderStatementParserRegistry(), jdbcTemplate);
        // Missing currency for the second row.
        String csv = "provider_reference,amount,currency\nPR-1,100,UGX\nPR-2,200,\n";

        validator.validate("MTN", "statement.csv", csv.getBytes(StandardCharsets.UTF_8));

        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(contains("provider_statement_validation_runs"), captor.capture());
        assertThat(captor.getValue().getValue("status")).isEqualTo("FAILED");
        assertThat(captor.getValue().getValue("valid")).isEqualTo(1);
        assertThat(captor.getValue().getValue("invalid")).isEqualTo(1);
    }

    @Test
    void aRepeatedReferenceAmountCurrencyKeyIsCountedAsADuplicate() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(1L);
        ProviderStatementValidator validator = new ProviderStatementValidator(new ProviderStatementParserRegistry(), jdbcTemplate);
        String csv = "provider_reference,amount,currency\nPR-1,100,UGX\nPR-1,100,UGX\n";

        validator.validate("MTN", "statement.csv", csv.getBytes(StandardCharsets.UTF_8));

        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(contains("provider_statement_validation_runs"), captor.capture());
        assertThat(captor.getValue().getValue("status")).isEqualTo("FAILED");
        assertThat(captor.getValue().getValue("valid")).isEqualTo(1);
        assertThat(captor.getValue().getValue("duplicates")).isEqualTo(1);
    }
}
