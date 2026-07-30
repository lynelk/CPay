package net.citotech.cito.api.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import net.citotech.cito.Common;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.api.v2.dto.AccountValidationRequest;
import net.citotech.cito.api.v2.dto.StatementExportResponse;
import net.citotech.cito.api.v2.dto.StatementExportResponse.StatementRow;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class MerchantFeatureServiceTest {

    @Test
    void accountValidationRequiresExplicitApiPrivilege() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        AccountValidationService service =
                new AccountValidationService(jdbcTemplate, mock(MerchantReadAuditService.class));
        AccountValidationRequest request = new AccountValidationRequest();
        request.setMerchantNumber("M100");
        request.setMsisdn("256770000000");

        assertThatThrownBy(() -> service.validate(request, merchant(Common.API_BALANCE_CHECK)))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining(Common.API_ACCOUNT_VALIDATION);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void statementExportRequiresExplicitApiPrivilege() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantStatementExportService service =
                new MerchantStatementExportService(
                        jdbcTemplate, mock(MerchantReadAuditService.class));

        assertThatThrownBy(
                        () ->
                                service.export(
                                        merchant(Common.API_BALANCE_CHECK),
                                        "M100",
                                        "2026-07-01",
                                        "2026-07-16",
                                        100))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining(Common.API_STATEMENT_EXPORT);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void statementCsvEscapesCommaAndQuoteCharacters() {
        MerchantStatementExportService service =
                new MerchantStatementExportService(
                        mock(NamedParameterJdbcTemplate.class),
                        mock(MerchantReadAuditService.class));
        StatementRow row = new StatementRow();
        row.setId(1L);
        row.setCreatedOn("2026-07-16 09:30:00");
        row.setDescription("Comma, and \"quote\"");
        row.setAmount(new BigDecimal("1000.00"));
        row.setCurrency("UGX");

        StatementExportResponse response = new StatementExportResponse();
        response.setRows(List.of(row));

        assertThat(service.toCsv(response)).contains("\"Comma, and \"\"quote\"\"\"");
    }

    @Test
    void statementXlsxIsReadableAndMatchesTheSourceRows() throws IOException {
        MerchantStatementExportService service =
                new MerchantStatementExportService(
                        mock(NamedParameterJdbcTemplate.class),
                        mock(MerchantReadAuditService.class));
        StatementRow row = new StatementRow();
        row.setId(42L);
        row.setCreatedOn("2026-07-16 09:30:00");
        row.setGatewayId("MTN_MOMO");
        row.setTransactionType("CR");
        row.setDescription("Sample payment");
        row.setAmount(new BigDecimal("1500.75"));
        row.setCurrency("UGX");

        StatementExportResponse response = new StatementExportResponse();
        response.setRows(List.of(row));

        byte[] bytes = service.toXlsx(response);
        assertThat(bytes).isNotEmpty();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("id");
            assertThat(header.getCell(3).getStringCellValue()).isEqualTo("transaction_type");
            assertThat(header.getCell(4).getStringCellValue()).isEqualTo("amount");

            Row dataRow = sheet.getRow(1);
            assertThat(dataRow.getCell(0).getNumericCellValue()).isEqualTo(42.0);
            assertThat(dataRow.getCell(1).getStringCellValue()).isEqualTo("2026-07-16 09:30:00");
            assertThat(dataRow.getCell(2).getStringCellValue()).isEqualTo("MTN_MOMO");
            assertThat(dataRow.getCell(3).getStringCellValue()).isEqualTo("CR");
            assertThat(dataRow.getCell(4).getNumericCellValue()).isEqualTo(1500.75);
            assertThat(dataRow.getCell(5).getStringCellValue()).isEqualTo("UGX");
        }
    }

    private Merchant merchant(String api) {
        Merchant merchant = new Merchant();
        merchant.setId(10L);
        merchant.setAccount_number("M100");
        merchant.setStatus("ACTIVE");
        merchant.setAllowed_apis(new String[] {api});
        return merchant;
    }
}
