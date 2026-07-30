package net.citotech.cito.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Covers audit O1: every per-provider parser shares this CSV/XLSX parsing logic (see {@link
 * MtnStatementParser} as the representative concrete subclass used here) - one real test of the
 * shared mechanics stands in for all five providers, since they only differ in the column aliases a
 * subclass supplies (covered separately by {@link SafaricomStatementParserTest}).
 */
class AbstractTabularStatementParserTest {

    private final ProviderStatementParser parser = new MtnStatementParser();

    @Test
    void parsesCsvRowsUsingTheFirstMatchingColumnAlias() {
        String csv =
                "provider_reference,merchant_reference,amount,currency,transaction_date\n"
                        + "PR-1,MR-1,1000.50,UGX,2026-07-01\n"
                        + "PR-2,MR-2,2000,UGX,2026-07-02\n";

        List<StatementRow> rows =
                parser.parse(csv.getBytes(StandardCharsets.UTF_8), "statement.csv");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).providerCode).isEqualTo("MTN");
        assertThat(rows.get(0).channelCode).isEqualTo("mtn_momo");
        assertThat(rows.get(0).providerReference).isEqualTo("PR-1");
        assertThat(rows.get(0).merchantReference).isEqualTo("MR-1");
        assertThat(rows.get(0).amount).isEqualByComparingTo(new BigDecimal("1000.50"));
        assertThat(rows.get(0).currency).isEqualTo("UGX");
        assertThat(rows.get(0).transactionDate).isEqualTo("2026-07-01");
        assertThat(rows.get(1).amount).isEqualByComparingTo(new BigDecimal("2000"));
    }

    @Test
    void parsesCsvUsingAnAlternateHeaderAlias() {
        // "transaction_id"/"receipt" for provider reference, "value" for amount, "ccy" for
        // currency - all alternate aliases the same column-lookup logic must also accept.
        String csv = "transaction_id,external_id,value,ccy,date\nTX-9,EXT-9,500,KES,2026-07-03\n";

        List<StatementRow> rows =
                parser.parse(csv.getBytes(StandardCharsets.UTF_8), "statement.csv");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).providerReference).isEqualTo("TX-9");
        assertThat(rows.get(0).merchantReference).isEqualTo("EXT-9");
        assertThat(rows.get(0).amount).isEqualByComparingTo(new BigDecimal("500"));
        assertThat(rows.get(0).currency).isEqualTo("KES");
    }

    @Test
    void returnsEmptyListForBlankOrHeaderOnlyCsv() {
        assertThat(parser.parse(new byte[0], "statement.csv")).isEmpty();
        assertThat(
                        parser.parse(
                                "provider_reference,amount,currency\n"
                                        .getBytes(StandardCharsets.UTF_8),
                                "statement.csv"))
                .isEmpty();
    }

    @Test
    void parseStringConvenienceOverloadDelegatesToByteParsing() {
        String csv = "provider_reference,amount,currency\nPR-1,100,UGX\n";

        List<StatementRow> rows = parser.parse(csv);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).providerReference).isEqualTo("PR-1");
    }

    @Test
    void parsesXlsxRowsTheSameWayAsCsv() throws Exception {
        byte[] xlsx =
                buildXlsx(
                        new String[] {
                            "provider_reference",
                            "merchant_reference",
                            "amount",
                            "currency",
                            "transaction_date"
                        },
                        new Object[][] {
                            {"PR-1", "MR-1", 1000.50, "UGX", "2026-07-01"},
                            {"PR-2", "MR-2", 2000.0, "UGX", "2026-07-02"}
                        });

        List<StatementRow> rows = parser.parse(xlsx, "statement.xlsx");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).providerReference).isEqualTo("PR-1");
        assertThat(rows.get(0).amount).isEqualByComparingTo(new BigDecimal("1000.5"));
        assertThat(rows.get(1).providerReference).isEqualTo("PR-2");
        assertThat(rows.get(1).amount).isEqualByComparingTo(new BigDecimal("2000"));
    }

    @Test
    void xlsxDetectionIsBasedOnFileExtensionNotContent() throws Exception {
        byte[] xlsx =
                buildXlsx(
                        new String[] {"provider_reference", "amount", "currency"},
                        new Object[][] {{"PR-1", 100.0, "UGX"}});

        // A ".csv" filename with XLSX bytes is parsed as CSV: the binary ZIP content decoded as
        // UTF-8 text won't contain the real header names, so no row can ever pick up the real
        // "PR-1" value - proving format detection is driven by the filename extension, not a
        // content sniff, without depending on exactly how many incidental newline bytes the
        // compressed binary happens to contain when misread as text.
        List<StatementRow> rows = parser.parse(xlsx, "statement.csv");

        assertThat(rows).noneMatch(row -> "PR-1".equals(row.providerReference));
    }

    private byte[] buildXlsx(String[] headers, Object[][] dataRows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Statement");
            Row headerRow = sheet.createRow(0);
            for (int c = 0; c < headers.length; c++) {
                headerRow.createCell(c).setCellValue(headers[c]);
            }
            for (int r = 0; r < dataRows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < dataRows[r].length; c++) {
                    Object value = dataRows[r][c];
                    if (value instanceof Double d) {
                        row.createCell(c).setCellValue(d);
                    } else {
                        row.createCell(c).setCellValue(String.valueOf(value));
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
