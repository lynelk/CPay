package net.citotech.cito.billing.export;

import java.math.BigDecimal;

/**
 * One row of the usage-event -&gt; rated-charge -&gt; invoice-line -&gt; invoice -&gt; ledger-entry
 * trace chain (see {@link BillingTraceChainService}). Because a {@code billing_invoices} row posts
 * one aggregate ledger transaction (not one entry per invoice line) at finalize time, a single
 * usage event/rated charge/invoice line legitimately produces one row per ledger entry in that
 * transaction (2 rows untaxed - DR ar + CR billing_revenue; 3 rows taxed, plus CR tax_payable) -
 * that fan-out is expected, not a query bug. The {@code ledger*} fields are {@code null} until the
 * invoice is finalized.
 */
public record BillingTraceChainRow(
        long usageEventId,
        long ratedChargeId,
        BigDecimal ratedChargeAmount,
        long invoiceLineId,
        BigDecimal invoiceLineAmount,
        long billingInvoiceId,
        String invoiceNumber,
        String invoiceStatus,
        Long ledgerTransactionId,
        Long ledgerEntryId,
        String ledgerEntryDirection,
        BigDecimal ledgerEntryAmount) {}
