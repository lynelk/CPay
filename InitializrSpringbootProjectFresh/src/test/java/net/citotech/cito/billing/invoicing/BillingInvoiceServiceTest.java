package net.citotech.cito.billing.invoicing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import net.citotech.cito.billing.integration.cpay.BillingLedgerAccountTemplateService;
import net.citotech.cito.billing.reconciliation.BillingCompletenessGateService;
import net.citotech.cito.billing.tax.BillingTaxSnapshot;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;

class BillingInvoiceServiceTest {

    private final BillingInvoiceRepository repository = mock(BillingInvoiceRepository.class);
    private final BillingCompletenessGateService completenessGateService =
            mock(BillingCompletenessGateService.class);
    private final BillingLedgerAccountTemplateService ledgerAccountTemplateService =
            mock(BillingLedgerAccountTemplateService.class);
    private final BillingInvoiceService service =
            new BillingInvoiceService(
                    repository, completenessGateService, ledgerAccountTemplateService);

    @Test
    void createDraftGeneratesAnInvoiceNumberAndDelegatesToTheRepository() {
        when(repository.insertDraft(
                        eq(7L),
                        any(),
                        eq("UGX"),
                        eq(LocalDate.of(2026, 1, 1)),
                        eq(LocalDate.of(2026, 1, 31))))
                .thenReturn(55L);
        assertThat(
                        service.createDraft(
                                7L, "ugx", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .isEqualTo(55L);
    }

    @Test
    void createDraftRejectsInvalidPeriodAndCurrency() {
        assertThatThrownBy(
                        () ->
                                service.createDraft(
                                        7L,
                                        "UGX",
                                        LocalDate.of(2026, 2, 1),
                                        LocalDate.of(2026, 1, 1)))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("period start");
        assertThatThrownBy(
                        () ->
                                service.createDraft(
                                        7L,
                                        " ",
                                        LocalDate.of(2026, 1, 1),
                                        LocalDate.of(2026, 1, 31)))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("currency");
    }

    @Test
    void stageChargesStagesEachUnstagedChargeAndRecomputesTotals() {
        when(repository.find(55L)).thenReturn(Optional.of(draftInvoice()));
        when(repository.findUnstagedCustomerCharges(
                        7L, "UGX", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .thenReturn(
                        List.of(
                                new UnstagedRatedCharge(
                                        201L, "PAYMENT", "collection_count", new BigDecimal("100")),
                                new UnstagedRatedCharge(
                                        202L, "SMS", "message_count", new BigDecimal("50"))));
        when(repository.sumLineAmounts(55L)).thenReturn(new BigDecimal("150"));

        assertThat(service.stageCharges(55L)).isEqualTo(2);
        verify(repository)
                .insertLine(55L, 201L, "PAYMENT:collection_count", new BigDecimal("100"), "UGX");
        verify(repository).insertLine(55L, 202L, "SMS:message_count", new BigDecimal("50"), "UGX");
        verify(repository).updateTotals(55L, new BigDecimal("150"), new BigDecimal("150"));
    }

    @Test
    void stageChargesNoOpAndStateGuardsWork() {
        when(repository.find(55L)).thenReturn(Optional.of(draftInvoice()));
        when(repository.findUnstagedCustomerCharges(
                        7L, "UGX", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .thenReturn(List.of());
        assertThat(service.stageCharges(55L)).isZero();
        verify(repository, never()).insertLine(anyLong(), any(), any(), any(), any());

        when(repository.find(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.stageCharges(99L))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("not found");

        when(repository.find(56L)).thenReturn(Optional.of(finalizedInvoice("150", "0", "150")));
        assertThatThrownBy(() -> service.stageCharges(56L))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("not DRAFT");
    }

    @Test
    void finalizeInvoiceRequiresApprovedCompletenessGate() {
        when(repository.find(55L)).thenReturn(Optional.of(draftInvoiceWithSubtotal("1000")));
        when(completenessGateService.isApproved(55L)).thenReturn(false);
        assertThatThrownBy(() -> service.finalizeInvoice(55L, "billing-finalizer"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("completeness gate is not approved");
        verify(ledgerAccountTemplateService, never())
                .postCustomerCharge(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void finalizeInvoiceFailsWhenCompletenessChangesAfterApproval() {
        when(repository.find(55L)).thenReturn(Optional.of(draftInvoiceWithSubtotal("1000")));
        when(completenessGateService.isApproved(55L)).thenReturn(true);
        when(completenessGateService.isFinalizationReady(55L)).thenReturn(false);

        assertThatThrownBy(() -> service.finalizeInvoice(55L, "billing-finalizer"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("controls changed after approval");
        verify(ledgerAccountTemplateService, never())
                .postCustomerCharge(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void finalizeInvoiceSnapshotsTaxPostsSplitAndFinalizes() {
        when(repository.find(55L)).thenReturn(Optional.of(draftInvoiceWithSubtotal("1000")));
        when(completenessGateService.isApproved(55L)).thenReturn(true);
        when(completenessGateService.isFinalizationReady(55L)).thenReturn(true);
        when(repository.sumLineAmounts(55L)).thenReturn(new BigDecimal("1000"));
        when(repository.calculateAndSnapshotTax(
                        eq(55L),
                        eq(7L),
                        eq("UGX"),
                        eq(LocalDate.of(2026, 1, 31)),
                        eq(new BigDecimal("1000"))))
                .thenReturn(
                        new BillingTaxSnapshot(
                                10L,
                                "STANDARD",
                                new BigDecimal("1000"),
                                new BigDecimal("0.18"),
                                new BigDecimal("180.0000"),
                                "UGX"));
        when(ledgerAccountTemplateService.postCustomerCharge(
                        eq(7L),
                        eq("UGX"),
                        eq(new BigDecimal("1000")),
                        eq(new BigDecimal("180.0000")),
                        eq("BINV-1"),
                        any()))
                .thenReturn(555L);
        when(repository.finalizeInvoice(55L, "billing-finalizer", 555L)).thenReturn(1);

        assertThat(service.finalizeInvoice(55L, "billing-finalizer")).isEqualTo(555L);
        verify(repository)
                .updateTaxAndTotals(
                        55L,
                        new BigDecimal("1000"),
                        new BigDecimal("180.0000"),
                        new BigDecimal("1180.0000"));
    }

    @Test
    void finalizeInvoiceFailsClosedWhenTaxRuleIsUnavailable() {
        when(repository.find(55L)).thenReturn(Optional.of(draftInvoiceWithSubtotal("1000")));
        when(completenessGateService.isApproved(55L)).thenReturn(true);
        when(completenessGateService.isFinalizationReady(55L)).thenReturn(true);
        when(repository.sumLineAmounts(55L)).thenReturn(new BigDecimal("1000"));
        when(repository.calculateAndSnapshotTax(anyLong(), anyLong(), any(), any(), any()))
                .thenThrow(new PaymentGatewayException("No approved billing tax rule"));
        assertThatThrownBy(() -> service.finalizeInvoice(55L, "billing-finalizer"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("tax rule");
        verify(ledgerAccountTemplateService, never())
                .postCustomerCharge(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void paymentReplayDoesNotPostTwice() {
        when(repository.find(55L)).thenReturn(Optional.of(finalizedInvoice("1000", "180", "1180")));
        when(repository.paymentAllocationExists(55L, "PAY-1")).thenReturn(true);
        assertThat(service.applyPayment(55L, "PAY-1", new BigDecimal("100"), "ops")).isZero();
        verify(ledgerAccountTemplateService, never())
                .postInvoicePayment(anyLong(), any(), any(), any(), any());
    }

    @Test
    void creditNoteRequiresMakerCheckerSeparation() {
        when(repository.find(55L)).thenReturn(Optional.of(finalizedInvoice("1000", "180", "1180")));
        assertThatThrownBy(
                        () ->
                                service.issueCreditNote(
                                        55L,
                                        new BigDecimal("118"),
                                        "Correction",
                                        "same-user",
                                        "same-user"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("different actors");
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

    private BillingInvoiceRecord draftInvoiceWithSubtotal(String subtotal) {
        return new BillingInvoiceRecord(
                55L,
                7L,
                "BINV-1",
                "UGX",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                "DRAFT",
                new BigDecimal(subtotal),
                BigDecimal.ZERO,
                new BigDecimal(subtotal),
                null,
                null,
                null);
    }

    private BillingInvoiceRecord finalizedInvoice(String subtotal, String tax, String total) {
        return new BillingInvoiceRecord(
                55L,
                7L,
                "BINV-1",
                "UGX",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                "FINALIZED",
                new BigDecimal(subtotal),
                new BigDecimal(tax),
                new BigDecimal(total),
                null,
                "admin1",
                900L);
    }
}
