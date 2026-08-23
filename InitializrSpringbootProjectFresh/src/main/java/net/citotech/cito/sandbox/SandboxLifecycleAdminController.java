package net.citotech.cito.sandbox;

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

/** Operations controls for certification review, production promotion and staged activation. */
@RestController
@RequestMapping(path = "/api/v2/admin/sandbox", produces = "application/json")
@PreAuthorize("hasRole('ADMIN')")
public class SandboxLifecycleAdminController {
    private final SandboxLifecycleService service;

    public SandboxLifecycleAdminController(SandboxLifecycleService service) {
        this.service = service;
    }

    @GetMapping("/go-live-requests")
    public List<Map<String, Object>> requests(
            @RequestParam(name = "status", required = false) String status) {
        return service.goLiveRequests(status);
    }

    @PostMapping("/go-live-requests/{requestId}/decision")
    public Map<String, Object> decision(
            @PathVariable long requestId, @RequestBody DecisionRequest request) {
        return service.advanceGoLiveRequest(
                requestId, request.action(), request.actor(), request.notes());
    }

    @PostMapping("/merchants/{merchantId}/promote-configuration")
    public Map<String, Object> promote(
            @PathVariable long merchantId, @RequestBody PromotionRequest request) {
        return service.promoteConfiguration(
                merchantId, request.goLiveRequestId(), request.actor());
    }

    @PostMapping("/merchants/{merchantId}/rollout")
    public Map<String, Object> rollout(
            @PathVariable long merchantId, @RequestBody RolloutRequest request) {
        return service.setRolloutStage(
                merchantId, request.stage(), request.actor(), request.dailyLimit());
    }

    @PostMapping("/merchants/{merchantId}/live-smoke-test")
    public Map<String, Object> smokeTest(
            @PathVariable long merchantId, @RequestBody SmokeTestRequest request) {
        return service.verifyLiveSmokeTest(
                merchantId,
                request.merchantNumber(),
                request.transactionReference(),
                request.actor());
    }

    @PostMapping("/verify-isolation")
    public Map<String, Object> verifyIsolation(@RequestBody IsolationRequest request) {
        return service.verifyIsolation(request.actor());
    }

    public record DecisionRequest(String action, String actor, String notes) {}

    public record PromotionRequest(Long goLiveRequestId, String actor) {}

    public record RolloutRequest(String stage, String actor, Integer dailyLimit) {}

    public record SmokeTestRequest(
            String merchantNumber, String transactionReference, String actor) {}

    public record IsolationRequest(String actor) {}
}
