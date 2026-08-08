package net.citotech.cito.billing.integration.cpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import net.citotech.cito.ledger.DoubleEntryLedgerService;
import net.citotech.cito.ledger.LedgerEntryCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BillingLedgerAccountTemplateServiceTest {

    private final DoubleEntryLedgerService ledgerService = mock(DoubleEntryLedgerService.class);
    private final BillingLedgerLinkWriter linkWriter = mock(BillingLedgerLinkWriter.class);
    private final BillingLedgerAccountTemplateService service =
            new BillingLedgerAccountTemplateService(ledgerService, linkWriter);

    @Test
    void postCustomerChargeWithoutTaxPostsTwoBalancedEntriesAndLinksAsCharge() {
        when(ledgerService.post(anyString(), anyString(), anyString(), anyString(), anyList()))
                .thenReturn(101L);

        long txId =
                service.postCustomerCharge(
                        7L,
                        "UGX",
                        new BigDecimal("1000"),
                        BigDecimal.ZERO,
                        "CHG-1",
                        "usage charge");

        assertThat(txId).isEqualTo(101L);
        List<LedgerEntryCommand> entries = capturedEntries();
        assertThat(entries).hasSize(2);
        assertBalanced(entries);
        assertThat(entries)
                .anySatisfy(e -> assertThat(e.accountCode()).isEqualTo("billing:7:UGX:ar"))
                .anySatisfy(e -> assertThat(e.accountCode()).isEqualTo("cpay:UGX:billing_revenue"));
        verify(linkWriter).write(101L, 7L, BillingLedgerLinkType.CHARGE, "CHG-1");
    }

    @Test
    void postCustomerChargeWithTaxAddsATaxPayableEntryAndStaysBalanced() {
        when(ledgerService.post(anyString(), anyString(), anyString(), anyString(), anyList()))
                .thenReturn(102L);

        service.postCustomerCharge(
                7L, "UGX", new BigDecimal("1000"), new BigDecimal("180"), "CHG-2", "usage + VAT");

        List<LedgerEntryCommand> entries = capturedEntries();
        assertThat(entries).hasSize(3);
        assertBalanced(entries);
        LedgerEntryCommand receivable =
                entries.stream()
                        .filter(e -> e.accountCode().equals("billing:7:UGX:ar"))
                        .findFirst()
                        .orElseThrow();
        assertThat(receivable.amount()).isEqualByComparingTo("1180");
        assertThat(entries)
                .anySatisfy(
                        e -> {
                            assertThat(e.accountCode()).isEqualTo("billing:7:UGX:tax_payable");
                            assertThat(e.amount()).isEqualByComparingTo("180");
                        });
    }

    @Test
    void postProviderCostAccrualPostsExpenseAgainstProviderPayableAndLinksAsCost() {
        when(ledgerService.post(anyString(), anyString(), anyString(), anyString(), anyList()))
                .thenReturn(103L);

        service.postProviderCostAccrual(
                7L, "UGX", new BigDecimal("50"), "mtn_momo", "COST-1", "provider fee accrual");

        List<LedgerEntryCommand> entries = capturedEntries();
        assertBalanced(entries);
        assertThat(entries)
                .anySatisfy(
                        e ->
                                assertThat(e.accountCode())
                                        .isEqualTo("cpay:UGX:provider_cost_expense"))
                .anySatisfy(
                        e ->
                                assertThat(e.accountCode())
                                        .isEqualTo("provider:mtn_momo:UGX:accrued_payable"));
        verify(linkWriter).write(103L, 7L, BillingLedgerLinkType.COST, "COST-1");
    }

    @Test
    void postPrepaidTopUpCreditsStoredValueLiabilityAndLinksAsPayment() {
        when(ledgerService.post(anyString(), anyString(), anyString(), anyString(), anyList()))
                .thenReturn(104L);

        service.postPrepaidTopUp(7L, "UGX", new BigDecimal("5000"), "PAY-1", "top-up");

        List<LedgerEntryCommand> entries = capturedEntries();
        assertBalanced(entries);
        assertThat(entries)
                .anySatisfy(
                        e -> {
                            assertThat(e.accountCode())
                                    .isEqualTo("billing:7:UGX:stored_value_liability");
                            assertThat(e.direction()).isEqualTo("CR");
                        });
        verify(linkWriter).write(104L, 7L, BillingLedgerLinkType.PAYMENT, "PAY-1");
    }

    @Test
    void postPrepaidConsumptionDebitsStoredValueLiabilityAndLinksAsCharge() {
        when(ledgerService.post(anyString(), anyString(), anyString(), anyString(), anyList()))
                .thenReturn(105L);

        service.postPrepaidConsumption(7L, "UGX", new BigDecimal("200"), "USG-1", "sms usage");

        List<LedgerEntryCommand> entries = capturedEntries();
        assertBalanced(entries);
        assertThat(entries)
                .anySatisfy(
                        e -> {
                            assertThat(e.accountCode())
                                    .isEqualTo("billing:7:UGX:stored_value_liability");
                            assertThat(e.direction()).isEqualTo("DR");
                        });
        verify(linkWriter).write(105L, 7L, BillingLedgerLinkType.CHARGE, "USG-1");
    }

    @Test
    void postInvoicePaymentClearsReceivableAndLinksAsInvoice() {
        when(ledgerService.post(anyString(), anyString(), anyString(), anyString(), anyList()))
                .thenReturn(106L);

        service.postInvoicePayment(7L, "UGX", new BigDecimal("1180"), "INV-1", "invoice paid");

        List<LedgerEntryCommand> entries = capturedEntries();
        assertBalanced(entries);
        assertThat(entries)
                .anySatisfy(
                        e -> {
                            assertThat(e.accountCode()).isEqualTo("billing:7:UGX:ar");
                            assertThat(e.direction()).isEqualTo("CR");
                        });
        verify(linkWriter).write(106L, 7L, BillingLedgerLinkType.INVOICE, "INV-1");
    }

    @Test
    void postCreditNoteReducesRevenueAndReceivableAndLinksAsReversal() {
        when(ledgerService.post(anyString(), anyString(), anyString(), anyString(), anyList()))
                .thenReturn(107L);

        service.postCreditNote(7L, "UGX", new BigDecimal("300"), "CN-1", "billing error credit");

        List<LedgerEntryCommand> entries = capturedEntries();
        assertBalanced(entries);
        assertThat(entries)
                .anySatisfy(
                        e -> {
                            assertThat(e.accountCode()).isEqualTo("cpay:UGX:billing_revenue");
                            assertThat(e.direction()).isEqualTo("DR");
                        });
        verify(linkWriter).write(107L, 7L, BillingLedgerLinkType.REVERSAL, "CN-1");
    }

    @Test
    void postBaasPlatformFeePostsSegregatedRevenueAndLinksAsCharge() {
        when(ledgerService.post(anyString(), anyString(), anyString(), anyString(), anyList()))
                .thenReturn(108L);

        service.postBaasPlatformFee(7L, "UGX", new BigDecimal("25"), "FEE-1", "platform fee");

        List<LedgerEntryCommand> entries = capturedEntries();
        assertBalanced(entries);
        assertThat(entries)
                .anySatisfy(
                        e ->
                                assertThat(e.accountCode())
                                        .isEqualTo("cpay:UGX:baas_platform_fee_revenue"));
        verify(linkWriter).write(108L, 7L, BillingLedgerLinkType.CHARGE, "FEE-1");
    }

    @SuppressWarnings("unchecked")
    private List<LedgerEntryCommand> capturedEntries() {
        ArgumentCaptor<List<LedgerEntryCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(ledgerService, times(1))
                .post(anyString(), anyString(), anyString(), anyString(), captor.capture());
        return captor.getValue();
    }

    private void assertBalanced(List<LedgerEntryCommand> entries) {
        BigDecimal debits =
                entries.stream()
                        .filter(e -> "DR".equals(e.direction()))
                        .map(LedgerEntryCommand::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits =
                entries.stream()
                        .filter(e -> "CR".equals(e.direction()))
                        .map(LedgerEntryCommand::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(debits).isEqualByComparingTo(credits);
    }
}
