package net.citotech.cito.reconciliation;

/**
 * MTN MoMo statement parser (audit O1). Column aliases are the shared defaults today — no real MTN
 * statement sample was available to diverge them — but a future MTN-specific quirk (a different
 * column name, date format, etc.) can be fixed here without touching any other provider's parser.
 */
public class MtnStatementParser extends AbstractTabularStatementParser {
    public MtnStatementParser() {
        super("MTN", "mtn_momo");
    }
}
