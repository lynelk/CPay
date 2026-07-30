package net.citotech.cito.reconciliation;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Audit O1: parses a provider statement file into {@link StatementRow}s. {@code parse(byte[],
 * String)} is the primary method — it dispatches on the file name's extension so a caller can hand
 * over either a CSV or an XLSX file without knowing which; {@code parse(String)} remains as a
 * convenience default for callers that already have decoded CSV text (existing tests, scripts).
 */
public interface ProviderStatementParser {
    String providerCode();

    String channelCode();

    List<StatementRow> parse(byte[] content, String fileName);

    default List<StatementRow> parse(String csvText) {
        return parse(csvText == null ? new byte[0] : csvText.getBytes(StandardCharsets.UTF_8), "statement.csv");
    }
}
