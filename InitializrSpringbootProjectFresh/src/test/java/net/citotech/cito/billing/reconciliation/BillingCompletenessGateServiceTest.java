package net.citotech.cito.billing.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.citotech.cito.billing.invoicing.BillingInvoiceRecord;
import net.citotech.cito.billing.invoicing.BillingInvoiceRepository;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@SuppressWarnings("unchecked")
class BillingCompletenessGateServiceTest {

    private final NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    private final BillingInvoiceRepository invoiceRepository = mock(BillingInvoiceRepository.class);
    private final BillingCompletenessGateService service =
            new BillingCompletenessGateService(jdbcTemplate, invoiceRepository);

    @Test
    void submitWritesPendingApprovalWithPassWhenNothingIsUnstaged() {
        when(invoiceRepository.find(55L)).thenReturn(Optional.of(draftInvoice()));
        when(invoiceRepository.countUnstagedCustomerCharges(
                        7L, "UGX", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .thenReturn(0);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(String.class)))
                .thenReturn("PENDING_APPROVAL");
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(9L);

        long id = service.submit(55L, "billing-maker");

        assertThat(id).isEqualTo(9L);
        verify(jdbcTemplate)
                .update(
                        contains("INSERT INTO billing_completeness_gates"),
                        (MapSqlParameterSource) argThatHasValue("completeness_result", "PASS"));
    }

    @Test
    void submitWritesPendingApprovalWithFailWhenChargesAreUnstaged() {
        when(invoiceRepository.find(55L)).thenReturn(Optional.of(draftInvoice()));
        when(invoiceRepository.countUnstagedCustomerCharges(
                        7L, "UGX", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .thenReturn(3);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(String.class)))
                .thenReturn("PENDING_APPROVAL");
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(9L);

        long id = service.submit(55L, "billing-maker");

        assertThat(id).isEqualTo(9L);
        verify(jdbcTemplate)
                .update(
                        contains("INSERT INTO billing_completeness_gates"),
                        (MapSqlParameterSource) argThatHasValue("completeness_result", "FAIL"));
    }

    @Test
    void submitThrowsWhenTheInvoiceIsNotFound() {
        when(invoiceRepository.find(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(99L, "billing-maker"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void submitThrowsWhenTheInvoiceIsNotDraft() {
        when(invoiceRepository.find(55L))
                .thenReturn(
                        Optional.of(
                                new BillingInvoiceRecord(
                                        55L,
                                        7L,
                                        "BINV-1",
                                        "UGX",
                                        LocalDate.of(2026, 1, 1),
                                        LocalDate.of(2026, 1, 31),
                                        "FINALIZED",
                                        BigDecimal.TEN,
                                        BigDecimal.ZERO,
                                        BigDecimal.TEN,
                                        null,
                                        "admin1",
                                        900L)));

        assertThatThrownBy(() -> service.submit(55L, "billing-maker"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("not DRAFT");
    }

    @Test
    void submitThrowsWhenTheGateIsAlreadyApproved() {
        when(invoiceRepository.find(55L)).thenReturn(Optional.of(draftInvoice()));
        when(invoiceRepository.countUnstagedCustomerCharges(
                        7L, "UGX", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .thenReturn(0);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(String.class)))
                .thenReturn("APPROVED");

        assertThatThrownBy(() -> service.submit(55L, "billing-maker"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("already approved");
    }

    @Test
    void submitThrowsWhenRequesterIsBlank() {
        assertThatThrownBy(() -> service.submit(55L, " "))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("requester");
    }

    @Test
    void approveWithADifferentActorAndAPassResultUpdatesTheRow() {
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(Map.of("completeness_result", "PASS")));
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        int updated = service.approve(55L, "billing-checker", null);

        assertThat(updated).isEqualTo(1);
        verify(jdbcTemplate)
                .update(contains("gate_status='APPROVED'"), any(MapSqlParameterSource.class));
    }

    @Test
    void approveOfAFailResultWithoutAWaiverReasonThrows() {
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(Map.of("completeness_result", "FAIL")));

        assertThatThrownBy(() -> service.approve(55L, "billing-checker", " "))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("waiver reason");
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void approveOfAFailResultWithAWaiverReasonSucceeds() {
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(Map.of("completeness_result", "FAIL")));
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        int updated = service.approve(55L, "billing-checker", "manually verified excluded charge");

        assertThat(updated).isEqualTo(1);
    }

    @Test
    void approveReturnsZeroWhenNoPendingGateExists() {
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of());

        int updated = service.approve(55L, "billing-checker", null);

        assertThat(updated).isZero();
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void rejectUpdatesTheRejectionReasonAndKeepsTheGatePending() {
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        int updated = service.reject(55L, "billing-checker", "missing SMS charges");

        assertThat(updated).isEqualTo(1);
        verify(jdbcTemplate)
                .update(contains("rejection_reason=:reason"), any(MapSqlParameterSource.class));
    }

    @Test
    void isApprovedReflectsTheStoredGateStatus() {
        when(jdbcTemplate.query(
                        anyString(),
                        any(MapSqlParameterSource.class),
                        any(org.springframework.jdbc.core.RowMapper.class)))
                .thenReturn(List.of("APPROVED"));

        assertThat(service.isApproved(55L)).isTrue();
    }

    @Test
    void isApprovedIsFalseWhenNoGateRowExists() {
        when(jdbcTemplate.query(
                        anyString(),
                        any(MapSqlParameterSource.class),
                        any(org.springframework.jdbc.core.RowMapper.class)))
                .thenReturn(List.of());

        assertThat(service.isApproved(55L)).isFalse();
    }

    private BillingInvoiceRecord draftInvoice() {
        return new BillingInvoiceRecord(
                55L,
                7L,
                "BINV-1",
                "UGX",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                "DRAFT",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                null);
    }

    private MapSqlParameterSource argThatHasValue(String key, String expectedValue) {
        return org.mockito.ArgumentMatchers.argThat(
                p -> p != null && expectedValue.equals(p.getValue(key)));
    }
}
