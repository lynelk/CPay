package net.citotech.cito.billing.invoicing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A row from {@code billing_invoices} (Flyway {@code V47}). */
public record BillingInvoiceRecord(
        long id,
        long billingTenantId,
        String invoiceNumber,
        String currency,
        LocalDate periodStart,
        LocalDate periodEnd,
        String status,
        BigDecimal subtotalAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        Instant finalizedAt,
        String finalizedBy,
        Long ledgerTransactionId) {

    public boolean isDraft() {
        return "DRAFT".equals(status);
    }
}
