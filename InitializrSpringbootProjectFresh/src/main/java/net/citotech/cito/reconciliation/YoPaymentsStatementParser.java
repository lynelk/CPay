package net.citotech.cito.reconciliation;

/**
 * Yo! Payments statement parser (audit O1). See {@link MtnStatementParser}'s javadoc for why column
 * aliases currently match the shared defaults.
 */
public class YoPaymentsStatementParser extends AbstractTabularStatementParser {
    public YoPaymentsStatementParser() {
        super("YO_PAYMENTS", "yo_payments");
    }
}
