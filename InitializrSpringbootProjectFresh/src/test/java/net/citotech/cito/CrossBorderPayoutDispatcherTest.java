package net.citotech.cito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CrossBorderPayoutDispatcherTest {

    @Test
    void approvedTransferCreatesDispatchEnvelopeAndMarksTransferSubmitted() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Integer.class), org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenReturn(0);
        when(jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(77L);
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("from cross_border_transfers"), org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenReturn(List.of(transfer("APPROVED")));
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("from corridor_routes"), org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenReturn(List.of(route()));
        CrossBorderPayoutDispatcher dispatcher = new CrossBorderPayoutDispatcher(jdbcTemplate);

        Map<String, Object> result = dispatcher.dispatch(42L, "ops");

        assertThat(result)
                .containsEntry("transferId", 42L)
                .containsEntry("dispatchId", 77L)
                .containsEntry("dispatchStatus", "READY_FOR_PROVIDER")
                .containsEntry("idempotencyKey", "XFER-42-UGKE-MTN");
        verify(jdbcTemplate).queryForObject(org.mockito.ArgumentMatchers.contains("idempotency_key"),
                org.mockito.ArgumentMatchers.eq(Integer.class),
                org.mockito.ArgumentMatchers.eq("XFER-42-UGKE-MTN"));
        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("status = 'SUBMITTED_TO_PARTNER'"),
                org.mockito.ArgumentMatchers.eq(42L));
    }

    @Test
    void duplicateDispatchIsRejectedBeforeInsert() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Integer.class), org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenReturn(1);
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("from cross_border_transfers"), org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenReturn(List.of(transfer("APPROVED")));
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("from corridor_routes"), org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenReturn(List.of(route()));
        CrossBorderPayoutDispatcher dispatcher = new CrossBorderPayoutDispatcher(jdbcTemplate);

        assertThatThrownBy(() -> dispatcher.dispatch(42L, "ops"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void invalidTransferStateIsRejected() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("from cross_border_transfers"), org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenReturn(List.of(transfer("CREATED")));
        CrossBorderPayoutDispatcher dispatcher = new CrossBorderPayoutDispatcher(jdbcTemplate);

        assertThatThrownBy(() -> dispatcher.dispatch(42L, "ops"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APPROVED");
    }

    @Test
    void providerSubmissionOnlyMarksReadyDispatches() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("cross_border_payout_rail_dispatches"), org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenReturn(List.of(Map.of("id", 5L, "transfer_id", 42L, "dispatch_status", "READY_FOR_PROVIDER")));
        when(jdbcTemplate.update(org.mockito.ArgumentMatchers.contains("dispatch_status = 'SUBMITTED'"),
                org.mockito.ArgumentMatchers.eq("PROV-1"),
                org.mockito.ArgumentMatchers.eq("{}"),
                org.mockito.ArgumentMatchers.eq(5L))).thenReturn(1);
        CrossBorderPayoutDispatcher dispatcher = new CrossBorderPayoutDispatcher(jdbcTemplate);

        Map<String, Object> result = dispatcher.markProviderSubmitted(5L, "PROV-1", "{}");

        assertThat(result).containsEntry("status", "SUBMITTED");
        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("provider_reference = coalesce"),
                org.mockito.ArgumentMatchers.eq("PROV-1"),
                org.mockito.ArgumentMatchers.eq(42L));
    }

    private Map<String, Object> transfer(String status) {
        Map<String, Object> transfer = new java.util.LinkedHashMap<>();
        transfer.put("id", 42L);
        transfer.put("transfer_reference", "XFER-REF");
        transfer.put("merchant_id", 100L);
        transfer.put("corridor_id", 9L);
        transfer.put("corridor_code", "UG-KE-UGX-KES");
        transfer.put("route_id", 3L);
        transfer.put("status", status);
        transfer.put("source_amount", new BigDecimal("1000.00"));
        transfer.put("destination_amount", new BigDecimal("3500.00"));
        transfer.put("destination_currency_code", "KES");
        transfer.put("destination_country_code", "KE");
        transfer.put("beneficiary_id", 55L);
        transfer.put("beneficiary_instrument_id", 56L);
        return transfer;
    }

    private Map<String, Object> route() {
        return Map.of("route_code", "UGKE-MTN", "provider_code", "MTN", "delivery_method", "MOBILE_MONEY");
    }
}
