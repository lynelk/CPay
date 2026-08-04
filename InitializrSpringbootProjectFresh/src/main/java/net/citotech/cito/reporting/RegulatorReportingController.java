package net.citotech.cito.reporting;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.citotech.cito.export.TabularExportService;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin regulator-reporting surface. The BoU daily cash-flow report is generated from the
 * normalized aggregated view of {@code merchant_transactions_log} (never an ad hoc dashboard
 * query), stored idempotently in {@code regulator_reports}, and downloadable as CSV. The PII
 * inventory endpoint is the in-app catalog of the PII data classes CPay holds - it contains only
 * metadata (data class, storage location, masking status), never personal data.
 */
@RestController
@RequestMapping(path = "/api/v2/admin/regulator")
@PreAuthorize("hasRole('ADMIN')")
public class RegulatorReportingController {

    private final RegulatorReportingService reportingService;

    public RegulatorReportingController(RegulatorReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping(path = "/daily-cash-flow")
    public ResponseEntity<?> dailyCashFlow(
            @RequestParam(name = "reportDate", required = false) String reportDate) {
        try {
            LocalDate date = parseDate(reportDate);
            Map<String, Object> report = reportingService.generateDailyCashFlow(date);
            return ResponseEntity.ok(report);
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.BAD_REQUEST, "REGULATOR_REPORT_REJECTED", e.getMessage());
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_REPORT_DATE", e.getMessage());
        }
    }

    @GetMapping(path = "/daily-cash-flow/csv")
    public ResponseEntity<?> dailyCashFlowCsv(
            @RequestParam(name = "reportDate", required = false) String reportDate) {
        try {
            LocalDate date = parseDate(reportDate);
            Map<String, Object> report = reportingService.fetchReport("BOU_DAILY_CASH_FLOW", date);
            if (report == null) {
                report = reportingService.generateDailyCashFlow(date);
            }
            String csv = reportingService.toCsv(report);
            String filename = "bou-daily-cash-flow-" + date + ".csv";
            return ResponseEntity.ok()
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType(TabularExportService.CSV_CONTENT_TYPE))
                    .body(csv);
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.BAD_REQUEST, "REGULATOR_REPORT_REJECTED", e.getMessage());
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_REPORT_DATE", e.getMessage());
        }
    }

    @GetMapping(path = "/reports")
    public List<Map<String, Object>> reports(
            @RequestParam(name = "reportType", required = false) String reportType,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return reportingService.listReports(reportType, limit);
    }

    @GetMapping(path = "/pii-inventory")
    public List<Map<String, Object>> piiInventory() {
        return reportingService.piiInventory();
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return LocalDate.now().minusDays(1);
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("reportDate must be ISO format yyyy-MM-dd");
        }
    }

    private ResponseEntity<Map<String, Object>> error(
            HttpStatus status, String code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        return ResponseEntity.status(status).body(error);
    }
}
