package net.citotech.cito.reconciliation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * Audit O1: shared CSV/XLSX parsing mechanics for a provider's statement file. Concrete per-provider
 * subclasses (one per adapter/channel — see {@link ProviderStatementParserRegistry}) supply only
 * {@link #columnAliases()}, so a provider-specific column-naming quirk can be fixed in one place
 * without touching how any other provider's statements are read. Column aliases are currently
 * identical across providers (no real per-provider statement samples were available to diverge them
 * further) — this class exists specifically so that changes, when a real quirk is reported, land in
 * one small subclass instead of a shared parser used by every provider at once.
 */
public abstract class AbstractTabularStatementParser implements ProviderStatementParser {
    private final String providerCode;
    private final String channelCode;

    protected AbstractTabularStatementParser(String providerCode, String channelCode) {
        this.providerCode = providerCode;
        this.channelCode = channelCode;
    }

    @Override
    public String providerCode() {
        return providerCode;
    }

    @Override
    public String channelCode() {
        return channelCode;
    }

    /**
     * Column name -> accepted header aliases (case-insensitive), checked in order. Subclasses
     * override to reflect a real provider's actual column naming once known.
     */
    protected Map<String, String[]> columnAliases() {
        Map<String, String[]> aliases = new HashMap<>();
        aliases.put("providerReference", new String[] {"provider_reference", "transaction_id", "receipt", "reference"});
        aliases.put("merchantReference", new String[] {"merchant_reference", "external_id", "client_reference", "merchant_ref"});
        aliases.put("amount", new String[] {"amount", "value", "transaction_amount"});
        aliases.put("currency", new String[] {"currency", "ccy"});
        aliases.put("transactionDate", new String[] {"transaction_date", "date", "completed_on"});
        return aliases;
    }

    @Override
    public List<StatementRow> parse(byte[] content, String fileName) {
        if (content == null || content.length == 0) {
            return new ArrayList<>();
        }
        if (isXlsx(fileName)) {
            return parseXlsx(content);
        }
        return parseCsv(new String(content, StandardCharsets.UTF_8));
    }

    private boolean isXlsx(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".xlsx");
    }

    private List<StatementRow> parseCsv(String csvText) {
        List<StatementRow> rows = new ArrayList<>();
        if (csvText == null || csvText.trim().isEmpty()) {
            return rows;
        }
        String[] lines = csvText.replace("\r", "").split("\n");
        if (lines.length < 2) {
            return rows;
        }
        Map<String, Integer> index = index(splitCsvLine(lines[0]));
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].trim().isEmpty()) {
                continue;
            }
            String[] values = splitCsvLine(lines[i]);
            rows.add(buildRow(field -> value(values, index, field)));
        }
        return rows;
    }

    private List<StatementRow> parseXlsx(byte[] content) {
        List<StatementRow> rows = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getLastRowNum() < 1) {
                return rows;
            }
            Map<String, Integer> index = index(headerCells(sheet.getRow(0)));
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                rows.add(buildRow(field -> cellValue(row, index, field)));
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to read XLSX statement: " + e.getMessage(), e);
        }
        return rows;
    }

    private StatementRow buildRow(java.util.function.Function<String, String> valueOf) {
        StatementRow row = new StatementRow();
        row.providerCode = providerCode;
        row.channelCode = channelCode;
        row.providerReference = valueOf.apply("providerReference");
        row.merchantReference = valueOf.apply("merchantReference");
        String amountText = valueOf.apply("amount");
        row.amount = amountText == null || amountText.isBlank()
                ? null
                : new BigDecimal(amountText.replace(",", ""));
        row.currency = valueOf.apply("currency");
        row.transactionDate = valueOf.apply("transactionDate");
        return row;
    }

    private String[] splitCsvLine(String line) {
        return line.split(",", -1);
    }

    private String[] headerCells(Row headerRow) {
        if (headerRow == null) {
            return new String[0];
        }
        int last = headerRow.getLastCellNum();
        String[] headers = new String[Math.max(last, 0)];
        for (int c = 0; c < headers.length; c++) {
            Cell cell = headerRow.getCell(c);
            headers[c] = cell == null ? "" : cellText(cell);
        }
        return headers;
    }

    private Map<String, Integer> index(String[] headers) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            map.put(headers[i].trim().toLowerCase(), i);
        }
        return map;
    }

    private String value(String[] values, Map<String, Integer> index, String field) {
        for (String name : columnAliases().getOrDefault(field, new String[0])) {
            Integer i = index.get(name);
            if (i != null && i < values.length) {
                return values[i].trim();
            }
        }
        return "";
    }

    private String cellValue(Row row, Map<String, Integer> index, String field) {
        for (String name : columnAliases().getOrDefault(field, new String[0])) {
            Integer i = index.get(name);
            if (i != null) {
                Cell cell = row.getCell(i);
                if (cell != null) {
                    return cellText(cell);
                }
            }
        }
        return "";
    }

    private String cellText(Cell cell) {
        return switch (cell.getCellType()) {
            case NUMERIC -> {
                double value = cell.getNumericCellValue();
                yield value == Math.floor(value) && !Double.isInfinite(value)
                        ? String.valueOf((long) value)
                        : String.valueOf(value);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> formulaText(cell);
            case BLANK -> "";
            default -> cell.getStringCellValue().trim();
        };
    }

    private String formulaText(Cell cell) {
        try {
            return cell.getCellType() == CellType.FORMULA && cell.getCachedFormulaResultType() == CellType.NUMERIC
                    ? String.valueOf(cell.getNumericCellValue())
                    : cell.getStringCellValue();
        } catch (Exception e) {
            return "";
        }
    }
}
