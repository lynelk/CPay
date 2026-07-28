package net.citotech.cito.reconciliation;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ProviderStatementParserRegistry {
    private final Map<String, ProviderStatementParser> parsers = new HashMap<>();

    public ProviderStatementParserRegistry() {
        register(new CsvStatementParser("MTN", "mtn_momo"));
        register(new CsvStatementParser("AIRTEL", "airtel_money"));
        register(new CsvStatementParser("AIRTEL_OPENAPI", "airtel_money_openapi"));
        register(new CsvStatementParser("SAFARICOM", "safaricom_mpesa"));
        register(new CsvStatementParser("YO_PAYMENTS", "yo_payments"));
    }

    private void register(ProviderStatementParser parser) {
        parsers.put(parser.providerCode(), parser);
    }

    public ProviderStatementParser get(String providerCode) {
        ProviderStatementParser parser = parsers.get(providerCode == null ? "" : providerCode.toUpperCase());
        if (parser == null) {
            throw new IllegalArgumentException("Unsupported provider statement: " + providerCode);
        }
        return parser;
    }
}

