package net.citotech.cito.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Regulator-reporting coverage: the BoU daily cash-flow report aggregates the normalized ledger, is
 * persisted idempotently, renders as CSV from the stored report_json (no PII), and the PII
 * inventory is metadata-only.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class RegulatorReportingServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void generateDailyCashFlowAggregatesAndPersistsTheReport() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        stubAggregate(jdbcTemplate);
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("id", 1L);
        stored.put("report_type", "BOU_DAILY_CASH_FLOW");
        stored.put("report_date", java.sql.Date.valueOf(LocalDate.of(2026, 8, 2)));
        stored.put("row_count", 2);
        stored.put("total_amount", new BigDecimal("15000"));
        stored.put("report_json", "{}");
        when(jdbcTemplate.queryForList(
                        contains("FROM regulator_reports WHERE"), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(stored));
        RegulatorReportingService service =
                new RegulatorReportingService(jdbcTemplate, OBJECT_MAPPER);

        Map<String, Object> report = service.generateDailyCashFlow(LocalDate.of(2026, 8, 2));

        assertThat(report).isNotNull();
        assertThat(report.get("report_type")).isEqualTo("BOU_DAILY_CASH_FLOW");
    }

    @Test
    void toCsvRendersRowsFromStoredReportJsonWithoutPii() throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        String json =
                "{\"reportDate\":\"2026-08-02\",\"rows\":["
                        + "{\"txType\":\"PAYIN\",\"currency\":\"UGX\",\"status\":\"SUCCESSFUL\","
                        + "\"txCount\":2,\"totalAmount\":10000},"
                        + "{\"txType\":\"PAYOUT\",\"currency\":\"UGX\",\"status\":\"SUCCESSFUL\","
                        + "\"txCount\":1,\"totalAmount\":5000}]}";
        report.put("report_json", json);
        RegulatorReportingService service =
                new RegulatorReportingService(
                        mock(NamedParameterJdbcTemplate.class), OBJECT_MAPPER);

        String csv = service.toCsv(report);

        assertThat(csv)
                .startsWith("tx_type,currency,status,tx_count,total_amount")
                .contains("PAYIN,UGX,SUCCESSFUL,2,10000")
                .contains("PAYOUT,UGX,SUCCESSFUL,1,5000");
    }

    @Test
    void toCsvRejectsMissingReports() {
        RegulatorReportingService service =
                new RegulatorReportingService(
                        mock(NamedParameterJdbcTemplate.class), OBJECT_MAPPER);

        assertThatThrownBy(() -> service.toCsv(null))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void piiInventoryListsOnlyMetadataEntries() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForList(
                        contains("FROM pii_inventory_entries"), any(MapSqlParameterSource.class)))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "data_class", "PAYER_MSISDN",
                                        "storage_location",
                                                "merchant_transactions_log.payer_number",
                                        "masking_status", "MASKED_IN_LOGS")));
        RegulatorReportingService service =
                new RegulatorReportingService(jdbcTemplate, OBJECT_MAPPER);

        List<Map<String, Object>> inventory = service.piiInventory();

        assertThat(inventory).hasSize(1);
        assertThat(inventory.get(0).get("data_class")).isEqualTo("PAYER_MSISDN");
    }

    /**
     * Stubs the service's private {@code Row} RowMapper (aggregation query) the same way the other
     * service tests do: the mapper is resolved out of the query invocation and applied to mocked
     * ResultSets.
     */
    private void stubAggregate(NamedParameterJdbcTemplate jdbcTemplate) throws Exception {
        ResultSet payin = mock(ResultSet.class);
        when(payin.getString("tx_type")).thenReturn("PAYIN");
        when(payin.getString("currency")).thenReturn("UGX");
        when(payin.getString("status")).thenReturn("SUCCESSFUL");
        when(payin.getInt("tx_count")).thenReturn(2);
        when(payin.getBigDecimal("total_amount")).thenReturn(new BigDecimal("10000"));
        ResultSet payout = mock(ResultSet.class);
        when(payout.getString("tx_type")).thenReturn("PAYOUT");
        when(payout.getString("currency")).thenReturn("UGX");
        when(payout.getString("status")).thenReturn("SUCCESSFUL");
        when(payout.getInt("tx_count")).thenReturn(1);
        when(payout.getBigDecimal("total_amount")).thenReturn(new BigDecimal("5000"));
        when(jdbcTemplate.query(
                        contains("GROUP BY tx_type, currency, status"),
                        any(MapSqlParameterSource.class),
                        any(RowMapper.class)))
                .thenAnswer(
                        invocation -> {
                            RowMapper mapper = invocation.getArgument(2);
                            return List.of(mapper.mapRow(payin, 0), mapper.mapRow(payout, 1));
                        });
    }
}
