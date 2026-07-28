package net.citotech.cito.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Covers the N10 failure-reason read path's ErrorCatalog annotation (only applied when a
 * structured error_code was extracted) and the O3 top-up recording write path.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class ReportingQueryServiceTest {

    @Test
    void annotatesRowsThatHaveAKnownErrorCodeWithCatalogMetadata() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ResultSet row = mock(ResultSet.class);
        when(row.getDate("stat_date")).thenReturn(java.sql.Date.valueOf(LocalDate.of(2026, 7, 27)));
        when(row.getString("gateway_id")).thenReturn("MTNMoMoPaymentGateway");
        when(row.getString("error_code")).thenReturn("111");
        when(row.getString("failure_reason")).thenReturn("Insufficient funds");
        when(row.getInt("tx_count")).thenReturn(4);

        when(jdbcTemplate.query(contains("daily_failure_reason_stats"), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(2);
                    return List.of(mapper.mapRow(row, 1));
                });

        ReportingQueryService service = new ReportingQueryService(jdbcTemplate);
        List<Map<String, Object>> rows = service.failureReasonStats(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 27), null);

        assertThat(rows).hasSize(1);
        Map<String, Object> annotated = rows.get(0);
        assertThat(annotated.get("errorCode")).isEqualTo("111");
        assertThat(annotated.get("stableErrorCode")).isEqualTo("PAYMENT_INSUFFICIENT_FUNDS");
        assertThat(annotated.get("errorCategory")).isEqualTo("business_rule");
        assertThat(annotated.get("retryable")).isEqualTo(false);
        assertThat(annotated.get("docsUrl")).asString().contains("Error-catalog.md");
    }

    @Test
    void leavesCatalogFieldsOffWhenNoStructuredErrorCodeWasExtracted() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ResultSet row = mock(ResultSet.class);
        when(row.getDate("stat_date")).thenReturn(java.sql.Date.valueOf(LocalDate.of(2026, 7, 27)));
        when(row.getString("gateway_id")).thenReturn("AirtelMoneyPaymentGateway");
        when(row.getString("error_code")).thenReturn("");
        when(row.getString("failure_reason")).thenReturn("Some raw unstructured provider trace text");
        when(row.getInt("tx_count")).thenReturn(1);

        when(jdbcTemplate.query(contains("daily_failure_reason_stats"), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(2);
                    return List.of(mapper.mapRow(row, 1));
                });

        ReportingQueryService service = new ReportingQueryService(jdbcTemplate);
        List<Map<String, Object>> rows = service.failureReasonStats(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 27), null);

        assertThat(rows).hasSize(1);
        Map<String, Object> notAnnotated = rows.get(0);
        assertThat(notAnnotated).doesNotContainKey("stableErrorCode");
        assertThat(notAnnotated).doesNotContainKey("errorCategory");
        assertThat(notAnnotated).doesNotContainKey("retryable");
    }

    @Test
    void recordTopupInsertsWithTodaysDateAndDefaultsRecordedByWhenBlank() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(contains("float_topups"), any(MapSqlParameterSource.class))).thenReturn(1);
        ReportingQueryService service = new ReportingQueryService(jdbcTemplate);

        int written = service.recordTopup("STOCK-001", new BigDecimal("500.00"), "  ", "manual top-up");

        assertThat(written).isEqualTo(1);
        org.mockito.ArgumentCaptor<MapSqlParameterSource> captor =
                org.mockito.ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(contains("INSERT INTO float_topups"), captor.capture());
        MapSqlParameterSource params = captor.getValue();
        assertThat(params.getValue("account")).isEqualTo("STOCK-001");
        assertThat(params.getValue("amount")).isEqualTo(new BigDecimal("500.00"));
        assertThat(params.getValue("recordedBy")).isEqualTo("system");
        assertThat(params.getValue("topupDate")).isEqualTo(java.sql.Date.valueOf(LocalDate.now()));
    }
}
