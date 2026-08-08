package net.citotech.cito.billing.integration.cpay;

/**
 * The {@code billing_ledger_links.link_type} values (ADR 0004 / {@code
 * V45__billing_ledger_links.sql}): what kind of billing artifact caused a given ledger posting.
 */
public enum BillingLedgerLinkType {
    CHARGE,
    INVOICE,
    PAYMENT,
    COST,
    REVERSAL
}
