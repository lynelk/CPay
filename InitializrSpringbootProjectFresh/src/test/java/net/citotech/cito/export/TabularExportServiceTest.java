package net.citotech.cito.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Covers audit M5: a single, reusable place to render tabular data as either CSV or XLSX, backing
 * every export surface (merchant statements today; more can adopt it later) instead of each
 * hand-building its own CSV string or, worse, a client-side spreadsheet shim. Verifies genuinely
 * correct output for both formats - not just "doesn't throw" - by parsing the XLSX bytes back
 * with POI's {@link XSSFWorkbook} and asserting on cell values/types.
 */
class TabularExportServiceTest {

    private record SampleRow(long id, String name, BigDecimal amount, String notes) {
    }

    private final TabularExportService service = new TabularExportService();

    private List<ExportColumn<SampleRow>> columns() {
        return List.of(
            ExportColumn.of("id", SampleRow::id),
            ExportColumn.of("name", SampleRow::name),
            ExportColumn.of("amount", SampleRow::amount),
            ExportColumn.of("notes", SampleRow::notes)
        );
    }

    @Test
    void csvContainsTheHeaderRowFollowedByEachDataRowInOrder() {
        List<SampleRow> rows = List.of(
            new SampleRow(1L, "Alice", new BigDecimal("100.50"), "first"),
            new SampleRow(2L, "Bob", new BigDecimal("200.00"), "second")
        );

        byte[] bytes = service.toCsv(columns(), rows);
        String csv = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);

        String[] lines = csv.split("\n");
        assertThat(lines).hasSize(3);
        assertThat(lines[0]).isEqualTo("id,name,amount,notes");
        assertThat(lines[1]).isEqualTo("1,Alice,100.50,first");
        assertThat(lines[2]).isEqualTo("2,Bob,200.00,second");
    }

    @Test
    void csvEscapesValuesContainingCommasQuotesOrNewlines() {
        List<SampleRow> rows = List.of(
            new SampleRow(1L, "Comma, and \"quote\"", BigDecimal.ONE, "line1\nline2")
        );

        byte[] bytes = service.toCsv(columns(), rows);
        String csv = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);

        assertThat(csv).contains("\"Comma, and \"\"quote\"\"\"");
        assertThat(csv).contains("\"line1\nline2\"");
    }

    @Test
    void csvRendersNullValuesAsEmptyFields() {
        List<SampleRow> rows = List.of(new SampleRow(1L, null, null, null));

        byte[] bytes = service.toCsv(columns(), rows);
        String csv = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);

        assertThat(csv.split("\n")[1]).isEqualTo("1,,,");
    }

    @Test
    void csvWithNoRowsIsJustTheHeader() {
        byte[] bytes = service.toCsv(columns(), List.of());
        String csv = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);

        assertThat(csv).isEqualTo("id,name,amount,notes\n");
    }

    @Test
    void xlsxRoundTripsHeaderAndRowsThroughPoi() throws IOException {
        List<SampleRow> rows = List.of(
            new SampleRow(1L, "Alice", new BigDecimal("100.50"), "first"),
            new SampleRow(2L, "Bob", new BigDecimal("200.00"), "second")
        );

        byte[] bytes = service.toXlsx("Sample Sheet", columns(), rows);
        assertThat(bytes).isNotEmpty();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("Sample Sheet");

            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("id");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("name");
            assertThat(header.getCell(2).getStringCellValue()).isEqualTo("amount");
            assertThat(header.getCell(3).getStringCellValue()).isEqualTo("notes");

            Row row1 = sheet.getRow(1);
            assertThat(row1.getCell(0).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(row1.getCell(0).getNumericCellValue()).isEqualTo(1.0);
            assertThat(row1.getCell(1).getStringCellValue()).isEqualTo("Alice");
            assertThat(row1.getCell(2).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(row1.getCell(2).getNumericCellValue()).isEqualTo(100.50);
            assertThat(row1.getCell(3).getStringCellValue()).isEqualTo("first");

            Row row2 = sheet.getRow(2);
            assertThat(row2.getCell(0).getNumericCellValue()).isEqualTo(2.0);
            assertThat(row2.getCell(1).getStringCellValue()).isEqualTo("Bob");
            assertThat(row2.getCell(2).getNumericCellValue()).isEqualTo(200.00);
            assertThat(row2.getCell(3).getStringCellValue()).isEqualTo("second");

            assertThat(sheet.getLastRowNum()).isEqualTo(2);
        }
    }

    @Test
    void xlsxRendersNullValuesAsEmptyStringCells() throws IOException {
        byte[] bytes = service.toXlsx("Sheet1", columns(), List.of(new SampleRow(1L, null, null, null)));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Row row = workbook.getSheetAt(0).getRow(1);
            Cell nameCell = row.getCell(1);
            assertThat(nameCell.getCellType()).isEqualTo(CellType.STRING);
            assertThat(nameCell.getStringCellValue()).isEmpty();
        }
    }

    @Test
    void xlsxSanitizesAndTruncatesInvalidSheetNames() throws IOException {
        String longName = "a".repeat(50) + "/bad\\name";
        byte[] bytes = service.toXlsx(longName, columns(), List.of());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            String sheetName = workbook.getSheetAt(0).getSheetName();
            assertThat(sheetName).hasSizeLessThanOrEqualTo(31);
            assertThat(sheetName).doesNotContain("/", "\\");
        }
    }
}
