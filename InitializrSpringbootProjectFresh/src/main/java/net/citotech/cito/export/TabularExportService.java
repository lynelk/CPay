package net.citotech.cito.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * Single reusable place to turn tabular data (a list of rows plus {@link ExportColumn} definitions)
 * into either a CSV or an XLSX byte payload (audit M5: "Export inconsistent across modules; CSV
 * only"). Before this, {@code MerchantStatementExportService} hand-built a CSV string, and several
 * portal/admin screens instead built a CSV client-side via a widget (Clientside {@code
 * shared/export/ExcelExport.js}) that is actually named/shaped like an Excel export but only ever
 * produced a CSV Blob in the browser - never a real spreadsheet, and never server-side. This
 * service gives every export surface one place to get both formats correctly.
 *
 * <p>This service only formats whatever rows it is given - it does not itself page through a data
 * source. Callers exporting a large date/transaction range are still responsible for bounding or
 * paginating the underlying query (see {@code MerchantStatementExportService}'s keyset cursor
 * pagination, audit D4) before handing rows here. The XLSX path uses a streaming {@link
 * SXSSFWorkbook} with a small in-memory row window so that formatting a bounded-but-large page does
 * not hold the whole workbook's XML DOM in memory at once, and any window rows spill to a temp file
 * POI cleans up via {@link SXSSFWorkbook#dispose()}.
 */
@Service
public class TabularExportService {

    public static final String CSV_CONTENT_TYPE = "text/csv;charset=UTF-8";
    public static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /**
     * Rows kept in memory at once by the streaming XLSX writer; older rows flush to a temp file.
     */
    private static final int STREAMING_ROW_WINDOW = 100;

    private static final int MAX_SHEET_NAME_LENGTH = 31;

    public <T> byte[] toCsv(List<ExportColumn<T>> columns, List<T> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeCsv(out, columns, rows);
        return out.toByteArray();
    }

    public <T> void writeCsv(
            OutputStream destination, List<ExportColumn<T>> columns, List<T> rows) {
        StringBuilder csv = new StringBuilder();
        appendCsvRow(csv, columns.stream().map(ExportColumn::header).toList());
        for (T row : rows) {
            appendCsvRow(
                    csv,
                    columns.stream().map(column -> formatForCsv(column.valueOf(row))).toList());
        }
        try {
            destination.write(csv.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public <T> byte[] toXlsx(String sheetName, List<ExportColumn<T>> columns, List<T> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeXlsx(out, sheetName, columns, rows);
        return out.toByteArray();
    }

    public <T> void writeXlsx(
            OutputStream destination,
            String sheetName,
            List<ExportColumn<T>> columns,
            List<T> rows) {
        SXSSFWorkbook workbook = new SXSSFWorkbook(STREAMING_ROW_WINDOW);
        try {
            Sheet sheet = workbook.createSheet(sanitizeSheetName(sheetName));
            Row headerRow = sheet.createRow(0);
            for (int col = 0; col < columns.size(); col++) {
                headerRow.createCell(col).setCellValue(columns.get(col).header());
            }
            int rowIndex = 1;
            for (T rowData : rows) {
                Row row = sheet.createRow(rowIndex++);
                for (int col = 0; col < columns.size(); col++) {
                    writeCell(row.createCell(col), columns.get(col).valueOf(rowData));
                }
            }
            workbook.write(destination);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            // Cleans up the temp file backing the streaming row window, in addition to the
            // in-memory workbook state - unlike a plain XSSFWorkbook, disposal is required here.
            workbook.dispose();
        }
    }

    private void appendCsvRow(StringBuilder csv, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(escapeCsv(values.get(i)));
        }
        csv.append('\n');
    }

    private String formatForCsv(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String escapeCsv(String value) {
        if (value.contains(",")
                || value.contains("\"")
                || value.contains("\n")
                || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private void writeCell(Cell cell, Object value) {
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof BigDecimal bigDecimal) {
            cell.setCellValue(bigDecimal.doubleValue());
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }

    private String sanitizeSheetName(String name) {
        if (name == null || name.isBlank()) {
            return "Export";
        }
        // Excel sheet names may not contain \ / * ? : [ ] and are capped at 31 characters.
        String sanitized = name.replaceAll("[\\\\/*?:\\[\\]]", "_");
        return sanitized.length() > MAX_SHEET_NAME_LENGTH
                ? sanitized.substring(0, MAX_SHEET_NAME_LENGTH)
                : sanitized;
    }
}
