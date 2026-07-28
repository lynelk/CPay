package net.citotech.cito.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.citotech.cito.admin.DeliveryOpsDashboardService.CallbackDeliveryRow;
import net.citotech.cito.admin.DeliveryOpsDashboardService.DeliveryOpsSummary;
import net.citotech.cito.admin.DeliveryOpsDashboardService.WebhookDeliveryRow;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class DeliveryOpsDashboardServiceTest {

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void summaryReportsCountsAndStuckRowsForBothDeliveryMechanismsSeparately() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);

        // First queryForList call is the legacy callback_tasks status breakdown,
        // second is the merchant_webhook_deliveries status breakdown.
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
            .thenReturn(List.of(Map.of("status", "PARKED", "cnt", 3), Map.of("status", "DONE", "cnt", 10)))
            .thenReturn(List.of(Map.of("status", "FAILED", "cnt", 2), Map.of("status", "DELIVERED", "cnt", 7)));

        // First query(...) call fetches stuck legacy callbacks, second fetches stuck webhook deliveries.
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of(new CallbackDeliveryRow(
                1L, 10L, "Acme Traders", "TX-1", "REF-1", "PARKED", 5, 5,
                "connection timeout", null, Instant.parse("2026-07-27T10:00:00Z"))))
            .thenReturn(List.of(new WebhookDeliveryRow(
                2L, 20L, "Beta Merchant", 99L, "payment.pending", "TX-2", "FAILED", 5,
                500, "Internal Server Error", null)));

        DeliveryOpsDashboardService service = new DeliveryOpsDashboardService(jdbcTemplate);

        DeliveryOpsSummary summary = service.summary(50);

        assertThat(summary.legacyCallbacks().countsByStatus())
            .containsEntry("PENDING", 0)
            .containsEntry("RETRY", 0)
            .containsEntry("PARKED", 3)
            .containsEntry("DONE", 10);
        assertThat(summary.legacyCallbacks().stuck()).hasSize(1);
        CallbackDeliveryRow callbackRow = summary.legacyCallbacks().stuck().get(0);
        assertThat(callbackRow.taskStatus()).isEqualTo("PARKED");
        assertThat(callbackRow.merchantName()).isEqualTo("Acme Traders");
        assertThat(callbackRow.attemptCount()).isEqualTo(5);
        assertThat(callbackRow.message()).isEqualTo("connection timeout");

        assertThat(summary.webhookDeliveries().countsByStatus())
            .containsEntry("PENDING", 0)
            .containsEntry("DELIVERED", 7)
            .containsEntry("FAILED", 2);
        assertThat(summary.webhookDeliveries().stuck()).hasSize(1);
        WebhookDeliveryRow webhookRow = summary.webhookDeliveries().stuck().get(0);
        assertThat(webhookRow.deliveryStatus()).isEqualTo("FAILED");
        assertThat(webhookRow.merchantName()).isEqualTo("Beta Merchant");
        assertThat(webhookRow.lastHttpStatus()).isEqualTo(500);
        assertThat(webhookRow.lastResponseSummary()).isEqualTo("Internal Server Error");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void clampsRequestedLimitToConfiguredBounds() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of());
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of());
        DeliveryOpsDashboardService service = new DeliveryOpsDashboardService(jdbcTemplate);

        service.summary(0);
        service.summary(9999);

        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate, times(4)).query(anyString(), captor.capture(), any(RowMapper.class));
        List<MapSqlParameterSource> captured = captor.getAllValues();

        // Calls 0 and 1 belong to summary(0) -> non-positive limit falls back to the default of 50.
        assertThat(captured.get(0).getValue("limit")).isEqualTo(50);
        assertThat(captured.get(1).getValue("limit")).isEqualTo(50);
        // Calls 2 and 3 belong to summary(9999) -> clamped to the max of 200.
        assertThat(captured.get(2).getValue("limit")).isEqualTo(200);
        assertThat(captured.get(3).getValue("limit")).isEqualTo(200);
    }

    @Test
    void countsByStatusReturnsZeroForKnownStatusesWithNoRows() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of());
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of());
        DeliveryOpsDashboardService service = new DeliveryOpsDashboardService(jdbcTemplate);

        DeliveryOpsSummary summary = service.summary(50);

        assertThat(summary.legacyCallbacks().countsByStatus())
            .containsEntry("PENDING", 0)
            .containsEntry("RETRY", 0)
            .containsEntry("PARKED", 0)
            .containsEntry("DONE", 0);
        assertThat(summary.legacyCallbacks().stuck()).isEmpty();
        assertThat(summary.webhookDeliveries().countsByStatus())
            .containsEntry("PENDING", 0)
            .containsEntry("DELIVERED", 0)
            .containsEntry("FAILED", 0);
        assertThat(summary.webhookDeliveries().stuck()).isEmpty();
    }
}
