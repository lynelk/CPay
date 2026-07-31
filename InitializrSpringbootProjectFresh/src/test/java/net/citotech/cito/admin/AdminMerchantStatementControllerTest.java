package net.citotech.cito.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.citotech.cito.api.v2.MerchantStatementExportService;
import net.citotech.cito.api.v2.dto.StatementExportResponse;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Covers audit M5: the admin portal's session-authenticated statement export endpoint. Mirrors the
 * plain-mock, no-Spring-context style of {@link ReadinessDashboardControllerTest} rather than a
 * full MockMvc slice, since the only behavior worth covering here (beyond the
 * {@code @PreAuthorize}/filter-chain wiring already covered by {@code SecurityConfig}) is: JSON
 * passthrough by default, CSV/XLSX rendering with the right content type and a filename in
 * Content-Disposition, and translating a rejected export into a 400 instead of leaking a raw
 * exception.
 */
class AdminMerchantStatementControllerTest {

    @Test
    void defaultsToJsonAndDelegatesAllParametersToTheService() {
        MerchantStatementExportService service = mock(MerchantStatementExportService.class);
        StatementExportResponse expected = new StatementExportResponse();
        when(service.exportForAdmin("1000003", "2026-01-01", "2026-01-31", 50, "cursor-1"))
                .thenReturn(expected);
        AdminMerchantStatementController controller = new AdminMerchantStatementController(service);

        ResponseEntity<?> response =
                controller.statements(
                        "1000003", "2026-01-01", "2026-01-31", "json", 50, "cursor-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(service).exportForAdmin("1000003", "2026-01-01", "2026-01-31", 50, "cursor-1");
    }

    @Test
    void rendersCsvWithAnAttachmentContentDispositionAndCsvContentType() {
        MerchantStatementExportService service = mock(MerchantStatementExportService.class);
        StatementExportResponse export = new StatementExportResponse();
        when(service.exportForAdmin("1000003", "2026-01-01", "2026-01-31", null, null))
                .thenReturn(export);
        when(service.toCsv(export)).thenReturn("id,amount\n1,100\n");
        AdminMerchantStatementController controller = new AdminMerchantStatementController(service);

        ResponseEntity<?> response =
                controller.statements("1000003", "2026-01-01", "2026-01-31", "csv", null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("id,amount\n1,100\n");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment")
                .contains("cpay-statement-1000003-2026-01-01-to-2026-01-31.csv");
    }

    @Test
    void rendersXlsxWithAnAttachmentContentDispositionAndXlsxContentType() {
        MerchantStatementExportService service = mock(MerchantStatementExportService.class);
        StatementExportResponse export = new StatementExportResponse();
        byte[] xlsxBytes = {1, 2, 3};
        when(service.exportForAdmin("1000003", "2026-01-01", "2026-01-31", null, null))
                .thenReturn(export);
        when(service.toXlsx(export)).thenReturn(xlsxBytes);
        AdminMerchantStatementController controller = new AdminMerchantStatementController(service);

        ResponseEntity<?> response =
                controller.statements("1000003", "2026-01-01", "2026-01-31", "xlsx", null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(xlsxBytes);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment")
                .contains("cpay-statement-1000003-2026-01-01-to-2026-01-31.xlsx");
    }

    @Test
    void translatesARejectedExportIntoABadRequestInsteadOfLeakingTheRawException() {
        MerchantStatementExportService service = mock(MerchantStatementExportService.class);
        when(service.exportForAdmin("unknown", "2026-01-01", "2026-01-31", null, null))
                .thenThrow(new PaymentGatewayException("Merchant not found: unknown"));
        AdminMerchantStatementController controller = new AdminMerchantStatementController(service);

        ResponseEntity<?> response =
                controller.statements("unknown", "2026-01-01", "2026-01-31", "json", null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isInstanceOf(java.util.Map.class);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> body = (java.util.Map<String, Object>) response.getBody();
        assertThat(body.get("code")).isEqualTo("STATEMENT_EXPORT_REJECTED");
        assertThat(body.get("message")).isEqualTo("Merchant not found: unknown");
    }
}
