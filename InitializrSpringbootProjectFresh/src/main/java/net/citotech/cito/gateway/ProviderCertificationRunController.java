package net.citotech.cito.gateway;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** P0 §1 run-lifecycle endpoints for the provider certification evidence workflow. */
@RestController
@RequestMapping(path = "/api/v2/admin/provider-certification/runs")
@PreAuthorize("hasRole('ADMIN')")
public class ProviderCertificationRunController {
    private final ProviderCertificationService service;

    public ProviderCertificationRunController(ProviderCertificationService service) {
        this.service = service;
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> payload) {
        long runId =
                service.createRun(
                        required(payload, "providerCode"),
                        required(payload, "channelCode"),
                        string(payload, "environment", "SANDBOX"),
                        string(payload, "scopeType", "GLOBAL"),
                        string(payload, "country", null),
                        string(payload, "currency", null),
                        string(payload, "createdBy", "system"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", runId);
        result.put("status", "DRAFT");
        return result;
    }

    @GetMapping
    public List<Map<String, Object>> list(
            @RequestParam(name = "provider", required = false) String provider,
            @RequestParam(name = "channel", required = false) String channel,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "limit", defaultValue = "100") int limit) {
        return service.listRuns(provider, channel, status, limit);
    }

    @GetMapping(path = "/{runId}")
    public Map<String, Object> get(@PathVariable long runId) {
        return service.getRun(runId);
    }

    @PostMapping(path = "/{runId}/start")
    public Map<String, Object> start(
            @PathVariable long runId,
            @RequestParam(name = "startedBy", required = false) String startedBy) {
        return service.startRun(runId, startedBy);
    }

    @PostMapping(path = "/{runId}/scenarios/{scenarioName}/result")
    public Map<String, Object> scenarioResult(
            @PathVariable long runId,
            @PathVariable String scenarioName,
            @RequestBody Map<String, Object> payload) {
        return service.recordScenarioResult(
                runId,
                scenarioName,
                string(payload, "scenarioResult", "PENDING"),
                string(payload, "observedStatus", null),
                string(payload, "notes", null),
                string(payload, "updatedBy", "system"));
    }

    @PostMapping(path = "/{runId}/evidence/{evidenceId}/link")
    public Map<String, Object> linkEvidence(
            @PathVariable long runId,
            @PathVariable long evidenceId,
            @RequestBody Map<String, Object> payload) {
        return service.linkEvidence(
                runId,
                evidenceId,
                string(payload, "scenarioName", "UNKNOWN"),
                string(payload, "updatedBy", "system"));
    }

    @PostMapping(path = "/{runId}/exceptions")
    public Map<String, Object> addException(
            @PathVariable long runId, @RequestBody Map<String, Object> payload) {
        return service.addException(
                runId,
                string(payload, "exceptionCode", "EXCEPTION"),
                string(payload, "exceptionType", "BLOCKING"),
                string(payload, "severity", "MEDIUM"),
                string(payload, "description", null),
                string(payload, "createdBy", "system"));
    }

    @PostMapping(path = "/{runId}/exceptions/{exceptionId}/resolve")
    public Map<String, Object> resolveException(
            @PathVariable long runId,
            @PathVariable long exceptionId,
            @RequestBody Map<String, Object> payload) {
        return service.resolveException(
                runId,
                exceptionId,
                string(payload, "resolution", null),
                string(payload, "resolvedBy", "system"));
    }

    @PostMapping(path = "/{runId}/submit")
    public Map<String, Object> submit(
            @PathVariable long runId,
            @RequestParam(name = "submittedBy", required = false) String submittedBy) {
        return service.submitForReview(runId, submittedBy);
    }

    @PostMapping(path = "/{runId}/approve")
    public Map<String, Object> approve(
            @PathVariable long runId,
            @RequestParam(name = "approvedBy", required = false) String approvedBy,
            @RequestParam(name = "expiresInDays", required = false) String expiresInDays) {
        return service.approveRun(runId, approvedBy, expiresInDays);
    }

    @PostMapping(path = "/{runId}/reject")
    public Map<String, Object> reject(
            @PathVariable long runId, @RequestBody Map<String, Object> payload) {
        return service.rejectRun(
                runId, string(payload, "reason", null), string(payload, "rejectedBy", "system"));
    }

    @GetMapping(path = "/readiness")
    public Map<String, Object> readiness(
            @RequestParam(name = "provider", required = false) String provider,
            @RequestParam(name = "channel", required = false) String channel) {
        return service.productionReadiness(
                provider == null ? "" : provider, channel == null ? "" : channel);
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
