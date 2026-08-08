package net.citotech.cito.billing.invoicing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Draft-invoice creation and charge staging for the {@code billing_invoices} domain (Flyway {@code
 * V47}) - the periodic billing statement, distinct from {@code checkout.InvoiceService}'s one-off
 * request-to-pay invoices. Finalizing a draft into an immutable, ledger-posted invoice is a
 * separate, later slice; this service only ever produces/mutates {@code DRAFT} invoices.
 */
@Service
public class BillingInvoiceService {
    private final BillingInvoiceRepository repository;

    public BillingInvoiceService(BillingInvoiceRepository repository) {
        this.repository = repository;
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
