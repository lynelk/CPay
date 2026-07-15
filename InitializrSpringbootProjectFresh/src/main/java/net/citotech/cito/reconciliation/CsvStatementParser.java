package net.citotech.cito.reconciliation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CsvStatementParser implements ProviderStatementParser {
    private final String providerCode;
    private final String channelCode;

    public CsvStatementParser(String providerCode, String channelCode) {
        this.providerCode = providerCode;
        this.channelCode = channelCode;
    }

    @Override
    public String providerCode() { return providerCode; }

    @Override
    public String channelCode() { return channelCode; }

    @Override
    public List<StatementRow> parse(String csvText) {
        List<StatementRow> rows = new ArrayList<>();
        if (csvText == null || csvText.trim().isEmpty()) return rows;
        String[] lines = csvText.replace("\r", "").split("\n");
        if (lines.length < 2) return rows;
        String[] headers = split(lines[0]);
        Map<String, Integer> index = index(headers);
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].trim().isEmpty()) continue;
            String[] values = split(lines[i]);
            StatementRow row = new StatementRow();
            row.providerCode = providerCode;
            row.channelCode = channelCode;
            row.providerReference = value(values, index, "provider_reference", "transaction_id", "receipt", "reference");
            row.merchantReference = value(values, index, "merchant_reference", "external_id", "client_reference", "merchant_ref");
            row.amount = new BigDecimal(value(values, index, "amount", "value", "transaction_amount").replace(",", ""));
            row.currency = value(values, index, "currency", "ccy");
            row.transactionDate = value(values, index, "transaction_date", "date", "completed_on");
            rows.add(row);
        }
        return rows;
    }

    private String[] split(String line) {
        return line.split(",", -1);
    }

    private Map<String, Integer> index(String[] headers) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            map.put(headers[i].trim().toLowerCase(), i);
        }
        return map;
    }

    private String value(String[] values, Map<String, Integer> index, String... names) {
        for (String name : names) {
            Integer i = index.get(name);
            if (i != null && i < values.length) return values[i].trim();
        }
        return "";
    }
}

