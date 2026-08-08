package net.citotech.cito.billing.invoicing;

import java.math.BigDecimal;

/**
 * A {@code billing_rated_charges} row (Flyway {@code V44}) not yet staged onto any {@code
 * billing_invoice_lines} row, as read by {@link
 * BillingInvoiceRepository#findUnstagedCustomerCharges}.
 */
record UnstagedRatedCharge(
        long ratedChargeId, String serviceCode, String meterCode, BigDecimal ratedAmount) {}
