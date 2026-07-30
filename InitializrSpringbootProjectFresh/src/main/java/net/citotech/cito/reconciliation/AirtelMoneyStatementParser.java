package net.citotech.cito.reconciliation;

/**
 * Airtel Money (legacy API) statement parser (audit O1). See {@link MtnStatementParser}'s javadoc
 * for why column aliases currently match the shared defaults.
 */
public class AirtelMoneyStatementParser extends AbstractTabularStatementParser {
    public AirtelMoneyStatementParser() {
        super("AIRTEL", "airtel_money");
    }
}
