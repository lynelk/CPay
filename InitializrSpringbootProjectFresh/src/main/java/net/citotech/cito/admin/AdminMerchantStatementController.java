package net.citotech.cito.admin;

import java.util.LinkedHashMap;
import java.util.Map;
import net.citotech.cito.api.v2.MerchantStatementExportService;
import net.citotech.cito.api.v2.dto.StatementExportResponse;
import net.citotech.cito.export.TabularExportService;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Audit M5: session-authenticated admin counterpart to the merchant self-service statement export
 * (see {@code MerchantSelfServiceController#statements}) and the v2-signed {@code
 * PaymentsV2Controller#statements}. Neither of those fits the admin portal: the self-service one
 * resolves the merchant from a merchant-portal session, and the v2 one requires HMAC request
 * signing meant for external API integrators - an admin operator browsing the portal has neither.
 * Method-level {@code @PreAuthorize} is defense-in-depth on top of the {@code /api/v2/admin/**} ->
 * {@code hasRole("ADMIN")} rule already enforced by {@code SecurityConfig}'s filter chain, matching
 * every other controller under this path (e.g. {@code ReadinessDashboardController}, {@code
 * ReconController}).
 */
@RestController
@RequestMapping(path = "/api/v2/admin/merchants")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMerchantStatementController {
    private final MerchantStatementExportService statementExportService;

    public AdminMerchantStatementController(MerchantStatementExportService statementExportService) {
        this.statementExportService = statementExportService;
    }

    @GetMapping(path = "/{merchantNumber}/statements")
    public ResponseEntity<?> statements(
            @PathVariable("merchantNumber") String merchantNumber,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            @RequestParam(value = "format", defaultValue = "json") String format,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "cursor", required = false) String cursor) {
        try {
            StatementExportResponse export =
                    statementExportService.exportForAdmin(
                            merchantNumber, startDate, endDate, limit, cursor);
            if ("csv".equalsIgnoreCase(format)) {
                return ResponseEntity.ok()
                        .header(
                                HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\""
                                        + filename(merchantNumber, startDate, endDate, "csv")
                                        + "\"")
                        .contentType(
                                MediaType.parseMediaType(TabularExportService.CSV_CONTENT_TYPE))
                        .body(statementExportService.toCsv(export));
            }
            if ("xlsx".equalsIgnoreCase(format)) {
                return ResponseEntity.ok()
                        .header(
                                HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\""
                                        + filename(merchantNumber, startDate, endDate, "xlsx")
                                        + "\"")
                        .contentType(
                                MediaType.parseMediaType(TabularExportService.XLSX_CONTENT_TYPE))
                        .body(statementExportService.toXlsx(export));
            }
            return ResponseEntity.ok(export);
        } catch (PaymentGatewayException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(error("STATEMENT_EXPORT_REJECTED", e.getMessage()));
        }
    }

    private String filename(
            String merchantNumber, String startDate, String endDate, String extension) {
        return "cpay-statement-"
                + merchantNumber
                + "-"
                + startDate
                + "-to-"
                + endDate
                + "."
                + extension;
    }

    private Map<String, Object> error(String code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        return error;
    }
}
