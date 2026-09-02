package net.citotech.cito.treasury;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.sharedprovider.SharedProviderAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

    @Test
    void sharedCollectionUsesCollectionSubAccountInsteadOfMasterOrDisbursement() {
        when(jdbc.queryForList(
                        contains("account_role=:account_role"), any(MapSqlParameterSource.class)))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "id", 31L,
                                        "book_balance", BigDecimal.ZERO,
                                        "reserved_balance", BigDecimal.ZERO,
                                        "pending_outgoing_balance", BigDecimal.ZERO)));
        when(jdbc.queryForList(
                        contains("provider_treasury_reservations WHERE idempotency_key"),
                        any(MapSqlParameterSource.class)))
                .thenReturn(List.of());
        when(jdbc.queryForObject(
                        contains("SELECT id FROM provider_treasury_reservations"),
                        any(MapSqlParameterSource.class),
                        eq(Long.class)))
                .thenReturn(77L);
        when(jdbc.update(any(String.class), any(MapSqlParameterSource.class))).thenReturn(1);
        Merchant merchant = new Merchant();
        merchant.setId(42L);
        merchant.setAccount_number("M-42");
        SharedProviderAccessService.CredentialContext context =
                new SharedProviderAccessService.CredentialContext(
                        SharedProviderAccessService.PLATFORM_SHARED,
                        Map.of(),
                        9L,
                        "UG",
                        "UGX",
                        "COLLECT");

        ProviderTreasuryService.Reservation reservation =
                service.beginShared(
                        context,
                        merchant,
                        "mtn_momo",
                        "PRODUCTION",
                        new BigDecimal("100.00"),
                        "ORDER-1");

        assertTrue(reservation.treasuryAccountId() == 31L);
        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).queryForList(contains("account_role=:account_role"), parameters.capture());
        assertTrue("COLLECTION".equals(parameters.getValue().getValue("account_role")));
    }
}
