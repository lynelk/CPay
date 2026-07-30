package net.citotech.cito.reconciliation;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ProviderStatementParserRegistry {
    private final Map<String, ProviderStatementParser> parsers = new HashMap<>();

    public ProviderStatementParserRegistry() {
        register(new MtnStatementParser());
        register(new AirtelMoneyStatementParser());
        register(new AirtelOpenApiStatementParser());
        register(new SafaricomStatementParser());
        register(new YoPaymentsStatementParser());
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

