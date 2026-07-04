package net.citotech.cito.reconciliation;

import java.util.List;

public interface ProviderStatementParser {
    String providerCode();
    String channelCode();
    List<StatementRow> parse(String csvText);
}
