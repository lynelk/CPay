package net.citotech.cito.billing.invoicing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;
import net.citotech.cito.billing.integration.cpay.BillingLedgerAccountTemplateService;
import net.citotech.cito.billing.integration.cpay.BillingPaymentFundingService;
import net.citotech.cito.billing.reconciliation.BillingCompletenessGateService;
import net.citotech.cito.billing.tax.BillingTaxSnapshot;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Governed periodic billing-invoice lifecycle. */
@Service
public class BillingInvoiceService {
    private final BillingInvoiceRepository repository;
    private final BillingCompletenessGateService completenessGateService;
    private final BillingLedgerAccountTemplateService ledgerAccountTemplateService;
    private final BillingPaymentFundingService fundingService;

    public BillingInvoiceService(
            BillingInvoiceRepository repository,
            BillingCompletenessGateService completenessGateService,
            BillingLedgerAccountTemplateService ledgerAccountTemplateService,
            BillingPaymentFundingService fundingService) {
        this.repository = repository;
        this.completenessGateService = completenessGateService;
        this.ledgerAccountTemplateService = ledgerAccountTemplateService;
        this.fundingService = fundingService;
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
        return repository.insertDraft(
                billingTenantId,
                "BINV-" + UUID.randomUUID(),
                currency.trim().toUpperCase(),
                periodStart,
                periodEnd);
    }

    @Transactional
    public int stageCharges(long billingInvoiceId) {
        BillingInvoiceRecord invoice = requireDraftInvoice(billingInvoiceId);
        var unstaged =
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
            repository.updateTotals(billingInvoiceId, subtotal, subtotal);
        }
        return unstaged.size();
    }

    @Transactional
    public long finalizeInvoice(long billingInvoiceId, String finalizedBy) {
        requireActor(finalizedBy, "Billing invoice finalize requires a finalizer");
        BillingInvoiceRecord invoice = requireDraftInvoice(billingInvoiceId);
        if (!completenessGateService.isApproved(billingInvoiceId)) {
            throw new PaymentGatewayException(
                    "Billing invoice "
                            + billingInvoiceId
                            + " completeness gate is not approved - submit and approve it first");
        }
        if (!completenessGateService.isFinalizationReady(billingInvoiceId)) {
            throw new PaymentGatewayException(
                    "Billing invoice "
                            + billingInvoiceId
                            + " completeness controls changed after approval - re-submit the gate");
        }

        BigDecimal subtotal = repository.sumLineAmounts(billingInvoiceId);
        if (subtotal.signum() <= 0) {
            throw new PaymentGatewayException("Billing invoice must contain a positive subtotal");
        }
        BillingTaxSnapshot tax =
                repository.calculateAndSnapshotTax(
                        billingInvoiceId,
                        invoice.billingTenantId(),
                        invoice.currency(),
                        invoice.periodEnd(),
                        subtotal);
        BigDecimal total = subtotal.add(tax.taxAmount());
        repository.updateTaxAndTotals(billingInvoiceId, subtotal, tax.taxAmount(), total);

        long ledgerTransactionId =
                ledgerAccountTemplateService.postCustomerCharge(
                        invoice.billingTenantId(),
                        invoice.currency(),
                        subtotal,
                        tax.taxAmount(),
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

    @Transactional
    public void markDelivered(long billingInvoiceId, String deliveredBy) {
        requireActor(deliveredBy, "Billing invoice delivery requires an actor");
        requireFinalizedInvoice(billingInvoiceId);
        if (repository.markDelivered(billingInvoiceId, deliveredBy.trim()) == 0) {
            throw new PaymentGatewayException(
                    "Billing invoice could not be marked delivered: " + billingInvoiceId);
        }
    }

    @Transactional
    public long applyPayment(
            long billingInvoiceId, String paymentReference, BigDecimal amount, String appliedBy) {
        requireActor(appliedBy, "Billing payment allocation requires an actor");
        requirePositive(amount, "Billing payment allocation amount must be positive");
        if (paymentReference == null || paymentReference.isBlank()) {
            throw new PaymentGatewayException("Billing payment reference is required");
        }
        BillingInvoiceRecord invoice = requireFinalizedInvoiceLocked(billingInvoiceId);
        BillingPaymentFundingService.FundingClaim claim =
                fundingService.claim(
                        invoice.billingTenantId(),
                        paymentReference,
                        invoice.currency(),
                        amount,
                        false,
                        "INVOICE",
                        String.valueOf(billingInvoiceId));
        if (claim.alreadyClaimed()) {
            return 0L;
        }
        BigDecimal outstanding = repository.findOutstandingAmount(billingInvoiceId);
        if (amount.compareTo(outstanding) > 0) {
            throw new PaymentGatewayException(
                    "Billing payment allocation exceeds invoice outstanding amount");
        }
        String allocationReference =
                invoice.invoiceNumber() + ":" + claim.sourceTransactionReference();
        long txId =
                ledgerAccountTemplateService.postInvoicePaymentFromMerchantCollection(
                        invoice.billingTenantId(),
                        claim.merchantId(),
                        invoice.currency(),
                        amount,
                        allocationReference,
                        "Payment allocated to " + invoice.invoiceNumber());
        repository.insertPaymentAllocation(
                invoice.billingTenantId(),
                billingInvoiceId,
                claim.sourceTransactionReference(),
                amount,
                invoice.currency(),
                txId);
        if (repository.reduceOutstanding(billingInvoiceId, amount) != 1) {
            throw new PaymentGatewayException(
                    "Billing invoice outstanding amount changed concurrently");
        }
        repository.closeIfSettled(billingInvoiceId, appliedBy.trim());
        return txId;
    }

    @Transactional
    public long issueCreditNote(
            long billingInvoiceId,
            BigDecimal grossAmount,
            String reason,
            String issuedBy,
            String approvedBy) {
        requirePositive(grossAmount, "Billing credit-note amount must be positive");
        requireActor(issuedBy, "Billing credit note requires an issuer");
        requireActor(approvedBy, "Billing credit note requires an approver");
        if (issuedBy.trim().equalsIgnoreCase(approvedBy.trim())) {
            throw new PaymentGatewayException(
                    "Billing credit-note issuer and approver must be different actors");
        }
        if (reason == null || reason.isBlank()) {
            throw new PaymentGatewayException("Billing credit-note reason is required");
        }
        BillingInvoiceRecord invoice = requireFinalizedInvoiceLocked(billingInvoiceId);
        BigDecimal outstanding = repository.findOutstandingAmount(billingInvoiceId);
        if (grossAmount.compareTo(outstanding) > 0) {
            throw new PaymentGatewayException(
                    "Billing credit note exceeds invoice outstanding amount");
        }
        BigDecimal taxCredit = proportionalTax(invoice, grossAmount);
        BigDecimal revenueCredit = grossAmount.subtract(taxCredit);
        String creditNumber = "BCN-" + UUID.randomUUID();
        long txId =
                ledgerAccountTemplateService.postCreditNoteWithTax(
                        invoice.billingTenantId(),
                        invoice.currency(),
                        revenueCredit,
                        taxCredit,
                        creditNumber,
                        "Credit note " + creditNumber + " for " + invoice.invoiceNumber());
        repository.insertPostedCreditNote(
                invoice.billingTenantId(),
                billingInvoiceId,
                creditNumber,
                invoice.currency(),
                grossAmount,
                reason.trim(),
                txId,
                issuedBy.trim(),
                approvedBy.trim());
        if (repository.reduceOutstanding(billingInvoiceId, grossAmount) != 1) {
            throw new PaymentGatewayException(
                    "Billing invoice outstanding amount changed concurrently");
        }
        repository.closeIfSettled(billingInvoiceId, approvedBy.trim());
        return txId;
    }

    @Transactional
    public long voidInvoice(
            long billingInvoiceId, String reason, String requestedBy, String approvedBy) {
        BillingInvoiceRecord invoice = requireFinalizedInvoice(billingInvoiceId);
        BigDecimal outstanding = repository.findOutstandingAmount(billingInvoiceId);
        if (outstanding.compareTo(invoice.totalAmount()) != 0) {
            throw new PaymentGatewayException(
                    "Only a completely unpaid billing invoice can be voided; use refund/correction workflow for settled invoices");
        }
        long creditTx =
                issueCreditNote(billingInvoiceId, outstanding, reason, requestedBy, approvedBy);
        if (repository.markVoid(billingInvoiceId, approvedBy.trim(), reason.trim()) == 0) {
            throw new PaymentGatewayException(
                    "Billing invoice could not be voided: " + billingInvoiceId);
        }
        return creditTx;
    }

    private BigDecimal proportionalTax(BillingInvoiceRecord invoice, BigDecimal grossAmount) {
        if (invoice.taxAmount() == null
                || invoice.taxAmount().signum() == 0
                || invoice.totalAmount() == null
                || invoice.totalAmount().signum() == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return grossAmount
                .multiply(invoice.taxAmount())
                .divide(invoice.totalAmount(), 4, RoundingMode.HALF_UP);
    }

    private BillingInvoiceRecord requireDraftInvoice(long billingInvoiceId) {
        BillingInvoiceRecord invoice = requireInvoice(billingInvoiceId);
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

    private BillingInvoiceRecord requireFinalizedInvoiceLocked(long billingInvoiceId) {
        BillingInvoiceRecord invoice =
                repository
                        .findForUpdate(billingInvoiceId)
                        .orElseThrow(
                                () ->
                                        new PaymentGatewayException(
                                                "Billing invoice not found: " + billingInvoiceId));
        if (!"FINALIZED".equals(invoice.status())) {
            throw new PaymentGatewayException(
                    "Billing invoice "
                            + billingInvoiceId
                            + " is not FINALIZED (status="
                            + invoice.status()
                            + ")");
        }
        return invoice;
    }

    private BillingInvoiceRecord requireFinalizedInvoice(long billingInvoiceId) {
        BillingInvoiceRecord invoice = requireInvoice(billingInvoiceId);
        if (!"FINALIZED".equals(invoice.status())) {
            throw new PaymentGatewayException(
                    "Billing invoice "
                            + billingInvoiceId
                            + " is not FINALIZED (status="
                            + invoice.status()
                            + ")");
        }
        return invoice;
    }

    private BillingInvoiceRecord requireInvoice(long billingInvoiceId) {
        return repository
                .find(billingInvoiceId)
                .orElseThrow(
                        () ->
                                new PaymentGatewayException(
                                        "Billing invoice not found: " + billingInvoiceId));
    }

    private void requireActor(String actor, String message) {
        if (actor == null || actor.isBlank()) {
            throw new PaymentGatewayException(message);
        }
    }

    private void requirePositive(BigDecimal amount, String message) {
        if (amount == null || amount.signum() <= 0) {
            throw new PaymentGatewayException(message);
        }
    }
}
