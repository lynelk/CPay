package net.citotech.cito.treasury;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class ProviderTreasuryServiceTest {
    private NamedParameterJdbcTemplate jdbc;
    private ProviderTreasuryService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        service = new ProviderTreasuryService(jdbc);
    }

    @Test
    void creditRequiresDestinationAccount() {
        PaymentGatewayException error =
                assertThrows(
                        PaymentGatewayException.class,
                        () ->
                                service.requestAdjustment(
                                        Map.of(
                                                "adjustmentType", "CREDIT",
                                                "amount", "100.00",
                                                "reason", "Provider settlement funding",
                                                "externalReference", "BANK-001",
                                                "valueDate", "2026-08-27"),
                                        "maker@example.com"));

        assertTrue(error.getMessage().contains("destinationAccountId"));
    }

    @Test
    void rebalanceRequiresDifferentAccounts() {
        PaymentGatewayException error =
                assertThrows(
                        PaymentGatewayException.class,
                        () ->
                                service.requestAdjustment(
                                        Map.of(
                                                "adjustmentType", "REBALANCE",
                                                "sourceAccountId", 11L,
                                                "destinationAccountId", 11L,
                                                "amount", "100.00",
                                                "reason", "Rebalance provider float",
                                                "externalReference", "REB-001",
                                                "valueDate", "2026-08-27"),
                                        "maker@example.com"));

        assertTrue(error.getMessage().contains("different sourceAccountId"));
    }

    @Test
    void makerCannotApproveOwnTreasuryAdjustment() {
        when(jdbc.queryForList(
                        contains("FROM provider_treasury_adjustments WHERE id=:id FOR UPDATE"),
                        any(MapSqlParameterSource.class)))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "id", 5L,
                                        "status", "PENDING",
                                        "requested_by", "maker@example.com")));

        PaymentGatewayException error =
                assertThrows(
                        PaymentGatewayException.class,
                        () -> service.approveAdjustment(5L, "maker@example.com"));

        assertTrue(error.getMessage().contains("Maker-checker"));
    }
}
