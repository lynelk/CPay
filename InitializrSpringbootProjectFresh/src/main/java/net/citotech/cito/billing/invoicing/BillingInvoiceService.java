package net.citotech.cito.billing.invoicing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import net.citotech.cito.billing.integration.cpay.BillingLedgerAccountTemplateService;
import net.citotech.cito.billing.reconciliation.BillingCompletenessGateService;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Draft-invoice creation, charge staging, and finalize for the {@code billing_invoices} domain
 * (Flyway {@code V47}) - the periodic billing statement, distinct from {@code
 * checkout.InvoiceService}'s one-off request-to-pay invoices. {@link #createDraft}/{@link
 * #stageCharges} only ever produce/mutate {@code DRAFT} invoices; {@link #finalizeInvoice} is the
 * one transition out of DRAFT, after which the invoice is immutable (enforced by {@code
 * requireDraftInvoice} rejecting any further staging).
 */
@Service
public class BillingInvoiceService {
    private final BillingInvoiceRepository repository;
    private final BillingCompletenessGateService completenessGateService;
    private final BillingLedgerAccountTemplateService ledgerAccountTemplateService;

    public BillingInvoiceService(
            BillingInvoiceRepository repository,
            BillingCompletenessGateService completenessGateService,
            BillingLedgerAccountTemplateService ledgerAccountTemplateService) {
        this.repository = repository;
        this.completenessGateService = completenessGateService;
        this.ledgerAccountTemplateService = ledgerAccountTemplateService;
    }

    @Transactional
    public long createDraft(
            long billingTenantId, String currency, LocalDate periodStart, LocalDate periodEnd) {
        if (currency == null || currency.isBlank()) {
            throw new PaymentGatewayException("Billing invoice currency is required");
        }
        if (periodStart == null || periodEnd == null || periodEnd.isBefore(periodStart)) {
            throw new PaymentGatewayException(
                    "Billing invoice period start must not be after period end");
        }
        String invoiceNumber = "BINV-" + UUID.randomUUID();
        return repository.insertDraft(
                billingTenantId,
                invoiceNumber,
                currency.trim().toUpperCase(),
                periodStart,
                periodEnd);
    }

    /**
     * Finds every {@code CUSTOMER_CHARGE} rated charge for this invoice's tenant/currency/period
     * not already staged onto an invoice line, stages each as a new line, and recomputes the
     * invoice's subtotal/total. Idempotent: rerunning after a partial success only stages what is
     * still unstaged, since {@code billing_invoice_lines.billing_rated_charge_id} is unique. Tax is
     * deliberately left at its default {@code 0} - a real tax adapter is out of scope for this
     * slice (stub adapters are explicitly acceptable at this stage of the billing plan).
     *
     * @return the number of newly staged lines
     */
    @Transactional
    public int stageCharges(long billingInvoiceId) {
        BillingInvoiceRecord invoice = requireDraftInvoice(billingInvoiceId);

        List<UnstagedRatedCharge> unstaged =
                repository.findUnstagedCustomerCharges(
                        invoice.billingTenantId(),
                        invoice.currency(),
                        invoice.periodStart(),
                        invoice.periodEnd());
        for (UnstagedRatedCharge charge : unstaged) {
            repository.insertLine(
                    billingInvoiceId,
                    charge.ratedChargeId(),
                    charge.serviceCode() + ":" + charge.meterCode(),
                    charge.ratedAmount(),
                    invoice.currency());
        }

        if (!unstaged.isEmpty()) {
            BigDecimal subtotal = repository.sumLineAmounts(billingInvoiceId);
            BigDecimal total = subtotal.add(invoice.taxAmount());
            repository.updateTotals(billingInvoiceId, subtotal, total);
        }
        return unstaged.size();
    }

    /**
     * Finalize workflow: requires the invoice still be DRAFT and its completeness gate {@code
     * APPROVED} ({@link BillingCompletenessGateService#isApproved}), posts the invoice total to the
     * ledger via {@link BillingLedgerAccountTemplateService#postCustomerCharge} using the invoice
     * number as the idempotent charge reference (already unique), then flips the row to {@code
     * FINALIZED} ({@link BillingInvoiceRepository#finalizeInvoice}, guarded {@code WHERE
     * status='DRAFT'} for race safety - if two calls race, the ledger post dedupes to the same
     * transaction id via {@code DoubleEntryLedgerService}'s own idempotency, and whichever caller
     * loses the DRAFT-guarded update throws, rolling back its entire transaction including any
     * {@code billing_ledger_links} row the ledger post wrote on its behalf).
     *
     * <p>Deliberately does not validate a zero subtotal itself: an invoice with nothing ever staged
     * trivially passes the completeness gate (zero unstaged charges) but will fail at {@code
     * DoubleEntryLedgerService.post()}'s own "entry amount must be greater than zero" check before
     * any row is written, since the whole method is {@code @Transactional} - that failure is the
     * intended guard, not a gap.
     *
     * @return the ledger transaction id the finalize posted
     */
    @Transactional
    public long finalizeInvoice(long billingInvoiceId, String finalizedBy) {
        if (finalizedBy == null || finalizedBy.isBlank()) {
            throw new PaymentGatewayException("Billing invoice finalize requires a finalizer");
        }
        BillingInvoiceRecord invoice = requireDraftInvoice(billingInvoiceId);
        if (!completenessGateService.isApproved(billingInvoiceId)) {
            throw new PaymentGatewayException(
                    "Billing invoice "
                            + billingInvoiceId
                            + " completeness gate is not approved - submit and approve it first");
        }

        long ledgerTransactionId =
                ledgerAccountTemplateService.postCustomerCharge(
                        invoice.billingTenantId(),
                        invoice.currency(),
                        invoice.subtotalAmount(),
                        invoice.taxAmount(),
                        invoice.invoiceNumber(),
                        "Billing invoice " + invoice.invoiceNumber() + " finalized");

        int updated =
                repository.finalizeInvoice(
                        billingInvoiceId, finalizedBy.trim(), ledgerTransactionId);
        if (updated == 0) {
            throw new PaymentGatewayException(
                    "Billing invoice "
                            + billingInvoiceId
                            + " was finalized by a concurrent request");
        }
        return ledgerTransactionId;
    }

    private BillingInvoiceRecord requireDraftInvoice(long billingInvoiceId) {
        BillingInvoiceRecord invoice =
                repository
                        .find(billingInvoiceId)
                        .orElseThrow(
                                () ->
                                        new PaymentGatewayException(
                                                "Billing invoice not found: " + billingInvoiceId));
        if (!invoice.isDraft()) {
            throw new PaymentGatewayException(
                    "Billing invoice "
                            + billingInvoiceId
                            + " is not DRAFT (status="
                            + invoice.status()
                            + ")");
        }
        return invoice;
    }
}
