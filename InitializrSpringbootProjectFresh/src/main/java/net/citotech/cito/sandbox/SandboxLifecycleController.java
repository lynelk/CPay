package net.citotech.cito.sandbox;

import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.citotech.cito.Model.MerchantUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Merchant self-service sandbox workbench. Every operation is tenant-scoped from the session. */
@RestController
@RequestMapping(path = "/api/v2/portal/sandbox", produces = "application/json")
public class SandboxLifecycleController {
    private final SandboxLifecycleService service;
    private final SandboxCleanupService cleanupService;
    private final SandboxCapabilityCatalogService capabilityCatalogService;
    private final SandboxGoLiveControlService goLiveControlService;

    public SandboxLifecycleController(
            SandboxLifecycleService service,
            SandboxCleanupService cleanupService,
            SandboxCapabilityCatalogService capabilityCatalogService,
            SandboxGoLiveControlService goLiveControlService) {
        this.service = service;
        this.cleanupService = cleanupService;
        this.capabilityCatalogService = capabilityCatalogService;
        this.goLiveControlService = goLiveControlService;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(HttpSession session) {
        MerchantUser user = merchant(session);
        Map<String, Object> result =
                new LinkedHashMap<>(service.summary(user.getMerchant_id(), user.getMerchant_number()));
        result.put(
                "catalog",
                capabilityCatalogService.catalog(user.getMerchant_id(), user.getMerchant_number()));
        return result;
    }

    @GetMapping("/catalog")
    public Map<String, Object> catalog(HttpSession session) {
        MerchantUser user = merchant(session);
        return capabilityCatalogService.catalog(user.getMerchant_id(), user.getMerchant_number());
    }

    @GetMapping("/readiness")
    public Object readiness(HttpSession session) {
        MerchantUser user = merchant(session);
        return service.summary(user.getMerchant_id(), user.getMerchant_number()).get("readiness");
    }

    @GetMapping("/wallets")
    public List<Map<String, Object>> wallets(HttpSession session) {
        return service.sandboxWallets(merchant(session).getMerchant_id());
    }

    @PostMapping("/wallets/top-up")
    public Map<String, Object> topUp(@RequestBody WalletTopUpRequest request, HttpSession session) {
        MerchantUser user = merchant(session);
        return service.topUp(
                user.getMerchant_id(),
                request.channelCode(),
                request.currency(),
                request.amount(),
                actor(user));
    }

    @PostMapping("/reset")
    public Map<String, Object> reset(@RequestBody ResetRequest request, HttpSession session) {
        MerchantUser user = merchant(session);
        Map<String, Object> result =
                new LinkedHashMap<>(
                        service.reset(
                                user.getMerchant_id(),
                                user.getMerchant_number(),
                                request.scope(),
                                actor(user)));
        if (request.scope() == null
                || request.scope().isBlank()
                || "ALL".equalsIgnoreCase(request.scope())) {
            result.putAll(cleanupService.resetFinancialSimulations(user.getMerchant_id()));
        }
        result.put("productionDataTouched", false);
        return result;
    }

    @GetMapping("/personas")
    public List<Map<String, Object>> personas(
            @RequestParam(name = "type", required = false) String type, HttpSession session) {
        merchant(session);
        return service.personas(type);
    }

    @GetMapping("/snapshots")
    public List<Map<String, Object>> snapshots(HttpSession session) {
        return service.snapshots(merchant(session).getMerchant_id());
    }

    @PostMapping("/snapshots")
    public Map<String, Object> createSnapshot(
            @RequestBody SnapshotRequest request, HttpSession session) {
        MerchantUser user = merchant(session);
        return service.createSnapshot(user.getMerchant_id(), request.name(), actor(user));
    }

    @PostMapping("/snapshots/{snapshotId}/restore")
    public Map<String, Object> restoreSnapshot(
            @PathVariable long snapshotId, HttpSession session) {
        MerchantUser user = merchant(session);
        return service.restoreSnapshot(user.getMerchant_id(), snapshotId, actor(user));
    }

    @PostMapping("/certification/run")
    public Map<String, Object> runCertification(HttpSession session) {
        MerchantUser user = merchant(session);
        return service.runCertification(user.getMerchant_id(), actor(user));
    }

    @GetMapping("/certification/latest")
    public Map<String, Object> latestCertification(HttpSession session) {
        return service.latestCertification(merchant(session).getMerchant_id());
    }

    @GetMapping("/environment-compare")
    public Map<String, Object> environmentCompare(HttpSession session) {
        MerchantUser user = merchant(session);
        return service.environmentComparison(user.getMerchant_id(), user.getMerchant_number());
    }

    @PostMapping("/production-access")
    public Map<String, Object> requestProductionAccess(HttpSession session) {
        MerchantUser user = merchant(session);
        return goLiveControlService.requestProductionAccess(user.getMerchant_id(), actor(user));
    }

    @GetMapping("/production-access/latest")
    public Map<String, Object> latestProductionAccess(HttpSession session) {
        return service.latestGoLiveRequest(merchant(session).getMerchant_id());
    }

    @GetMapping("/rollout")
    public Map<String, Object> rollout(HttpSession session) {
        return service.rollout(merchant(session).getMerchant_id());
    }

    private MerchantUser merchant(HttpSession session) {
        Object value = session == null ? null : session.getAttribute("merchantUser");
        if (!(value instanceof MerchantUser user) || user.getMerchant_id() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Merchant session required.");
        }
        return user;
    }

    private String actor(MerchantUser user) {
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail();
        }
        if (user.getName() != null && !user.getName().isBlank()) {
            return user.getName();
        }
        return "merchant-user-" + user.getId();
    }

    public record WalletTopUpRequest(String channelCode, String currency, BigDecimal amount) {}

    public record ResetRequest(String scope) {}

    public record SnapshotRequest(String name) {}
}
