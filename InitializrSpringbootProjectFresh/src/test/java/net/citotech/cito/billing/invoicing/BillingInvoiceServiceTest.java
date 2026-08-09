package net.citotech.cito.billing.invoicing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import net.citotech.cito.billing.integration.cpay.BillingLedgerAccountTemplateService;
import net.citotech.cito.billing.reconciliation.BillingCompletenessGateService;
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

        long id =
                service.createDraft(7L, "ugx", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertThat(id).isEqualTo(55L);
        verify(repository)
                .insertDraft(
                        eq(7L),
                        any(),
                        eq("UGX"),
                        eq(LocalDate.of(2026, 1, 1)),
                        eq(LocalDate.of(2026, 1, 31)));
    }

    @Test
    void createDraftRejectsAPeriodEndBeforePeriodStart() {
        assertThatThrownBy(
                        () ->
                                service.createDraft(
                                        7L,
                                        "UGX",
                                        LocalDate.of(2026, 2, 1),
                                        LocalDate.of(2026, 1, 1)))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("period start");
    }

    @Test
    void createDraftRejectsABlankCurrency() {
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
    void stageChargesInsertsALineForEachUnstagedChargeAndRecomputesTotals() {
        BillingInvoiceRecord draft = draftInvoice();
        when(repository.find(55L)).thenReturn(Optional.of(draft));
        when(repository.findUnstagedCustomerCharges(
                        7L, "UGX", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .thenReturn(
                        List.of(
                                new UnstagedRatedCharge(
                                        201L, "PAYMENT", "collection_count", new BigDecimal("100")),
                                new UnstagedRatedCharge(
                                        202L, "SMS", "message_count", new BigDecimal("50"))));
        when(repository.sumLineAmounts(55L)).thenReturn(new BigDecimal("150"));

        int staged = service.stageCharges(55L);

        assertThat(staged).isEqualTo(2);
        verify(repository)
                .insertLine(55L, 201L, "PAYMENT:collection_count", new BigDecimal("100"), "UGX");
        verify(repository).insertLine(55L, 202L, "SMS:message_count", new BigDecimal("50"), "UGX");
        verify(repository).updateTotals(55L, new BigDecimal("150"), new BigDecimal("150"));
    }

    @Test
    void stageChargesIsANoOpWhenNothingIsUnstaged() {
        when(repository.find(55L)).thenReturn(Optional.of(draftInvoice()));
        when(repository.findUnstagedCustomerCharges(
                        7L, "UGX", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .thenReturn(List.of());

        int staged = service.stageCharges(55L);

        assertThat(staged).isZero();
        verify(repository, never()).insertLine(anyLong(), any(), any(), any(), any());
        verify(repository, never()).updateTotals(anyLong(), any(), any());
    }

    @Test
    void stageChargesThrowsWhenTheInvoiceIsNotFound() {
        when(repository.find(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.stageCharges(99L))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void stageChargesThrowsWhenTheInvoiceIsAlreadyFinalized() {
        when(repository.find(55L))
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
                                        new BigDecimal("150"),
                                        BigDecimal.ZERO,
                                        new BigDecimal("150"),
                                        null,
                                        "admin1",
                                        900L)));

        assertThatThrownBy(() -> service.stageCharges(55L))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("not DRAFT");
        verify(repository, times(0)).findUnstagedCustomerCharges(anyLong(), any(), any(), any());
    }

    @Test
    void finalizeInvoiceThrowsWhenFinalizerIsBlank() {
        assertThatThrownBy(() -> service.finalizeInvoice(55L, " "))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("finalizer");
    }

    @Test
    void finalizeInvoiceThrowsWhenTheInvoiceIsNotFound() {
        when(repository.find(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.finalizeInvoice(99L, "billing-finalizer"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void finalizeInvoiceThrowsWhenTheInvoiceIsNotDraft() {
        when(repository.find(55L))
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
                                        new BigDecimal("1000"),
                                        BigDecimal.ZERO,
                                        new BigDecimal("1000"),
                                        null,
                                        "admin1",
                                        900L)));

        assertThatThrownBy(() -> service.finalizeInvoice(55L, "billing-finalizer"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("not DRAFT");
    }

    @Test
    void finalizeInvoiceThrowsWhenTheCompletenessGateIsNotApproved() {
        when(repository.find(55L)).thenReturn(Optional.of(draftInvoiceWithSubtotal("1000")));
        when(completenessGateService.isApproved(55L)).thenReturn(false);

        assertThatThrownBy(() -> service.finalizeInvoice(55L, "billing-finalizer"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("completeness gate is not approved");
        verify(ledgerAccountTemplateService, never())
                .postCustomerCharge(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void finalizeInvoiceHappyPathPostsToTheLedgerAndFinalizesTheRepositoryRow() {
        when(repository.find(55L)).thenReturn(Optional.of(draftInvoiceWithSubtotal("1000")));
        when(completenessGateService.isApproved(55L)).thenReturn(true);
        when(ledgerAccountTemplateService.postCustomerCharge(
                        eq(7L),
                        eq("UGX"),
                        eq(new BigDecimal("1000")),
                        eq(BigDecimal.ZERO),
                        eq("BINV-1"),
                        any()))
                .thenReturn(555L);
        when(repository.finalizeInvoice(55L, "billing-finalizer", 555L)).thenReturn(1);

        long ledgerTransactionId = service.finalizeInvoice(55L, "billing-finalizer");

        assertThat(ledgerTransactionId).isEqualTo(555L);
        verify(repository).finalizeInvoice(55L, "billing-finalizer", 555L);
    }

    @Test
    void finalizeInvoiceThrowsWhenTheRepositoryLosesTheFinalizeRace() {
        when(repository.find(55L)).thenReturn(Optional.of(draftInvoiceWithSubtotal("1000")));
        when(completenessGateService.isApproved(55L)).thenReturn(true);
        when(ledgerAccountTemplateService.postCustomerCharge(
                        anyLong(), any(), any(), any(), any(), any()))
                .thenReturn(555L);
        when(repository.finalizeInvoice(anyLong(), any(), eq(555L))).thenReturn(0);

        assertThatThrownBy(() -> service.finalizeInvoice(55L, "billing-finalizer"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("concurrent request");
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
}
