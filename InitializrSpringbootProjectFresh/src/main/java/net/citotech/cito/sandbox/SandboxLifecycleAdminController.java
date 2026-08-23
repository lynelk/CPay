package net.citotech.cito.sandbox;

import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
    private final SandboxProductionGuardService productionGuard;

    public SandboxLifecycleAdminController(
            SandboxLifecycleService service, SandboxProductionGuardService productionGuard) {
        this.service = service;
        this.productionGuard = productionGuard;
    }

    @GetMapping("/go-live-requests")
    public List<Map<String, Object>> requests(
            @RequestParam(name = "status", required = false) String status) {
        return service.goLiveRequests(status);
    }

    @PostMapping("/go-live-requests/{requestId}/decision")
    public Map<String, Object> decision(
            @PathVariable long requestId,
            @RequestBody DecisionRequest request,
            Authentication authentication) {
        String actor = actor(authentication);
        productionGuard.assertDecisionAllowed(requestId, request.action(), actor);
        return service.advanceGoLiveRequest(requestId, request.action(), actor, request.notes());
    }

    @PostMapping("/merchants/{merchantId}/promote-configuration")
    public Map<String, Object> promote(
            @PathVariable long merchantId,
            @RequestBody PromotionRequest request,
            Authentication authentication) {
        return service.promoteConfiguration(
                merchantId, request.goLiveRequestId(), actor(authentication));
    }

    @PostMapping("/merchants/{merchantId}/rollout")
    public Map<String, Object> rollout(
            @PathVariable long merchantId,
            @RequestBody RolloutRequest request,
            Authentication authentication) {
        productionGuard.assertRolloutStageAllowed(merchantId, request.stage());
        return service.setRolloutStage(
                merchantId, request.stage(), actor(authentication), request.dailyLimit());
    }

    @PostMapping("/merchants/{merchantId}/live-smoke-test")
    public Map<String, Object> smokeTest(
            @PathVariable long merchantId,
            @RequestBody SmokeTestRequest request,
            Authentication authentication) {
        return service.verifyLiveSmokeTest(
                merchantId,
                request.merchantNumber(),
                request.transactionReference(),
                actor(authentication));
    }

    @PostMapping("/verify-isolation")
    public Map<String, Object> verifyIsolation(Authentication authentication) {
        return service.verifyIsolation(actor(authentication));
    }

    private String actor(Authentication authentication) {
        return authentication == null || authentication.getName() == null
                ? "authenticated-admin"
                : authentication.getName();
    }

    public record DecisionRequest(String action, String notes) {}

    public record PromotionRequest(Long goLiveRequestId) {}

    public record RolloutRequest(String stage, Integer dailyLimit) {}

    public record SmokeTestRequest(String merchantNumber, String transactionReference) {}
}
