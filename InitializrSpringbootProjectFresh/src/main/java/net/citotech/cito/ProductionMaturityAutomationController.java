package net.citotech.cito;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Automation endpoints that connect the P1-P4 database foundations to executable workflows.
 */
@RestController
@RequestMapping(path = "/api/v2/production-maturity", produces = "application/json")
public class ProductionMaturityAutomationController {

    private final ScreeningProviderAdapterRegistry screeningProviderAdapterRegistry;
    private final CrossBorderPayoutDispatcher crossBorderPayoutDispatcher;
    private final SettlementPostingAutomationService settlementPostingAutomationService;
    private final JdbcTemplate jdbcTemplate;

    public ProductionMaturityAutomationController(
        ScreeningProviderAdapterRegistry screeningProviderAdapterRegistry,
        CrossBorderPayoutDispatcher crossBorderPayoutDispatcher,
        SettlementPostingAutomationService settlementPostingAutomationService,
        JdbcTemplate jdbcTemplate
    ) {
        this.screeningProviderAdapterRegistry = screeningProviderAdapterRegistry;
        this.crossBorderPayoutDispatcher = crossBorderPayoutDispatcher;
        this.settlementPostingAutomationService = settlementPostingAutomationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/screening/providers")
    public Map<String, Object> registerScreeningProvider(@RequestBody Map<String, Object> payload) {
        return screeningProviderAdapterRegistry.registerProvider(payload);
    }

    @PostMapping("/screening/requests")
    public ScreeningProviderAdapterRegistry.ScreeningResult requestScreening(@RequestBody Map<String, Object> payload) {
        return screeningProviderAdapterRegistry.screen(
            new ScreeningProviderAdapterRegistry.ScreeningRequest(
                required(payload, "providerCode"),
                required(payload, "subjectType"),
                required(payload, "subjectReference"),
                required(payload, "screeningType"),
                string(payload, "payload", "{}"),
                string(payload, "requestedBy", "system")
            )
        );
    }

    @PostMapping("/cross-border/transfers/{transferId}/dispatch")
    public Map<String, Object> dispatchCrossBorderTransfer(
        @PathVariable Long transferId,
        @RequestParam(defaultValue = "system") String requestedBy
    ) {
        return crossBorderPayoutDispatcher.dispatch(transferId, requestedBy);
    }

    @GetMapping("/cross-border/dispatches/pending")
    public List<Map<String, Object>> pendingCrossBorderDispatches(@RequestParam(defaultValue = "50") int limit) {
        return crossBorderPayoutDispatcher.pendingDispatches(limit);
    }

    @PostMapping("/cross-border/dispatches/{dispatchId}/provider-submitted")
    public Map<String, Object> markCrossBorderProviderSubmitted(
        @PathVariable Long dispatchId,
        @RequestBody Map<String, Object> payload
    ) {
        return crossBorderPayoutDispatcher.markProviderSubmitted(
            dispatchId,
            required(payload, "providerReference"),
            string(payload, "responsePayload", "{}")
        );
    }

    @PostMapping("/settlements/finance/{settlementBatchId}/post")
    public Map<String, Object> postFinanceSettlement(
        @PathVariable Long settlementBatchId,
        @RequestParam(defaultValue = "system") String postedBy
    ) {
        return settlementPostingAutomationService.postFinanceSettlement(settlementBatchId, postedBy);
    }

    @PostMapping("/settlements/corridor/{settlementBatchId}/post")
    public Map<String, Object> postCorridorSettlement(
        @PathVariable Long settlementBatchId,
        @RequestParam(defaultValue = "system") String postedBy
    ) {
        return settlementPostingAutomationService.postCorridorSettlement(settlementBatchId, postedBy);
    }

    @GetMapping("/settlements/posting-runs")
    public List<Map<String, Object>> postingRuns(@RequestParam(defaultValue = "100") int limit) {
        return settlementPostingAutomationService.postingRuns(limit);
    }

    @PostMapping("/validation/runs")
    public Map<String, Object> createValidationRun(@RequestBody Map<String, Object> payload) {
        Long runId = jdbcTemplate.queryForObject(
            "insert into production_maturity_validation_runs (run_type, run_status, source_ref, checked_by, summary) "
                + "values (?, 'RUNNING', ?, ?, ?) returning id",
            Long.class,
            required(payload, "runType"),
            string(payload, "sourceRef", null),
            string(payload, "checkedBy", "system"),
            string(payload, "summary", null)
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", runId);
        result.put("status", "RUNNING");
        result.put("createdAt", Instant.now().toString());
        return result;
    }

    @PostMapping("/validation/runs/{runId}/results")
    public Map<String, Object> addValidationResult(@PathVariable Long runId, @RequestBody Map<String, Object> payload) {
        Long resultId = jdbcTemplate.queryForObject(
            "insert into production_maturity_validation_results "
                + "(run_id, check_code, check_name, check_status, severity, details, evidence_ref) "
                + "values (?, ?, ?, ?, ?, ?, ?) returning id",
            Long.class,
            runId,
            required(payload, "checkCode"),
            required(payload, "checkName"),
            required(payload, "checkStatus"),
            string(payload, "severity", "MEDIUM"),
            string(payload, "details", null),
            string(payload, "evidenceRef", null)
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", runId);
        result.put("resultId", resultId);
        result.put("status", "RECORDED");
        return result;
    }

    @PostMapping("/validation/runs/{runId}/complete")
    public Map<String, Object> completeValidationRun(@PathVariable Long runId, @RequestBody Map<String, Object> payload) {
        String status = string(payload, "runStatus", "COMPLETED");
        jdbcTemplate.update(
            "update production_maturity_validation_runs set run_status = ?, summary = coalesce(?, summary), "
                + "completed_at = current_timestamp where id = ?",
            status,
            string(payload, "summary", null),
            runId
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", runId);
        result.put("status", status);
        return result;
    }

    @GetMapping("/validation/runs")
    public List<Map<String, Object>> validationRuns(@RequestParam(defaultValue = "100") int limit) {
        return jdbcTemplate.queryForList(
            "select id, run_type, run_status, source_ref, checked_by, summary, started_at, completed_at "
                + "from production_maturity_validation_runs order by started_at desc limit ?",
            limit
        );
    }

    private static String required(Map<String, Object> payload, String key) {
        String value = string(payload, key, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    private static String string(Map<String, Object> payload, String key, String defaultValue) {
        Object value = payload == null ? null : payload.get(key);
        return value == null ? defaultValue : value.toString();
    }
}
