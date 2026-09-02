package net.citotech.cito.treasury;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import net.citotech.cito.admin.AdminPermissionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlled provider-float administration. There is intentionally no "set balance" endpoint.
 * Credits, debits and rebalances are maker-checker requests that post only after approval.
 */
@RestController
@RequestMapping("/api/v2/admin/provider-treasury")
@PreAuthorize("hasRole('ADMIN')")
public class ProviderTreasuryAdminController {
    private final ProviderTreasuryService service;
    private final AdminPermissionService permissions;
    private final ProviderLiveTestService liveTests;
    private final ProviderBalanceRefreshService balanceRefresh;

    public ProviderTreasuryAdminController(
            ProviderTreasuryService service,
            AdminPermissionService permissions,
            ProviderLiveTestService liveTests,
            ProviderBalanceRefreshService balanceRefresh) {
        this.service = service;
        this.permissions = permissions;
        this.liveTests = liveTests;
        this.balanceRefresh = balanceRefresh;
    }

    @GetMapping("/accounts")
    public List<Map<String, Object>> accounts() {
        permissions.require("PAYMENT_BALANCE_VIEW", "provider-balance-list", "all");
        return service.listAccounts();
    }

    @PostMapping("/accounts/{id}/refresh-provider-balance")
    public Map<String, Object> refreshProviderBalance(@PathVariable long id) {
        permissions.require("PAYMENT_BALANCE_REFRESH", "provider-balance-refresh", "account:" + id);
        return balanceRefresh.refresh(id);
    }

    @GetMapping("/adjustments")
    public List<Map<String, Object>> adjustments() {
        permissions.require("RECONCILIATION_VIEW", "provider-adjustment-list", "all");
        return service.listAdjustments();
    }

    @PostMapping("/adjustments")
    public Map<String, Object> requestAdjustment(
            @RequestBody Map<String, Object> body, Authentication authentication) {
        permissions.require("RECONCILIATION_MANAGE", "provider-adjustment-request", "treasury");
        return service.requestAdjustment(body, actor(authentication));
    }

    @PostMapping("/adjustments/{id}/approve")
    public Map<String, Object> approveAdjustment(
            @PathVariable long id, Authentication authentication) {
        permissions.require(
                "RECONCILIATION_MANAGE", "provider-adjustment-approve", "adjustment:" + id);
        return service.approveAdjustment(id, actor(authentication));
    }

    @PostMapping("/adjustments/{id}/reject")
    public Map<String, Object> rejectAdjustment(
            @PathVariable long id, Authentication authentication) {
        permissions.require(
                "RECONCILIATION_MANAGE", "provider-adjustment-reject", "adjustment:" + id);
        return service.rejectAdjustment(id, actor(authentication));
    }

    @GetMapping("/reservations")
    public List<Map<String, Object>> reservations() {
        permissions.require("PAYMENT_BALANCE_VIEW", "provider-reservation-list", "all");
        return service.listReservations();
    }

    @PostMapping("/reservations/{id}/resolve")
    public Map<String, Object> resolveReservation(
            @PathVariable long id,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        permissions.require(
                "RECONCILIATION_MANAGE", "provider-reservation-resolve", "reservation:" + id);
        boolean success =
                Boolean.TRUE.equals(body.get("success"))
                        || "true".equalsIgnoreCase(String.valueOf(body.get("success")));
        return service.resolvePending(
                id,
                success,
                String.valueOf(body.getOrDefault("providerReference", "")),
                actor(authentication));
    }

    @GetMapping("/exposures")
    public List<Map<String, Object>> exposures() {
        permissions.require("PAYMENT_BALANCE_VIEW", "provider-exposure-list", "all");
        return service.listExposures();
    }

    @GetMapping("/journal")
    public List<Map<String, Object>> journal() {
        permissions.require("RECONCILIATION_VIEW", "provider-journal-list", "all");
        return service.listJournal();
    }

    @GetMapping("/reconciliations")
    public List<Map<String, Object>> reconciliations() {
        permissions.require("RECONCILIATION_VIEW", "provider-reconciliation-list", "all");
        return service.listReconciliations();
    }

    @PostMapping("/accounts/{id}/reconcile")
    public Map<String, Object> reconcile(
            @PathVariable long id,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        permissions.require("RECONCILIATION_MANAGE", "provider-reconcile", "account:" + id);
        return service.reconcile(id, body, actor(authentication));
    }

    @PostMapping("/accounts/{id}/low-float-threshold")
    public Map<String, Object> lowFloatThreshold(
            @PathVariable long id, @RequestBody Map<String, Object> body) {
        permissions.require("RECONCILIATION_MANAGE", "provider-threshold-update", "account:" + id);
        Object raw = body.get("lowFloatThreshold");
        BigDecimal threshold = raw == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(raw));
        return service.setLowFloatThreshold(id, threshold);
    }

    @GetMapping("/merchants")
    public List<Map<String, Object>> merchants() {
        permissions.require("PAYMENT_BALANCE_VIEW", "provider-test-merchant-list", "all");
        return liveTests.merchants();
    }

    @GetMapping("/live-tests")
    public List<Map<String, Object>> liveTests() {
        permissions.require("PAYMENT_BALANCE_VIEW", "provider-live-test-list", "all");
        return liveTests.list();
    }

    @PostMapping("/live-tests")
    public Map<String, Object> requestLiveTest(
            @RequestBody Map<String, Object> body, Authentication authentication) {
        return liveTests.request(body, actor(authentication));
    }

    @PostMapping("/live-tests/{id}/approve")
    public Map<String, Object> approveLiveTest(
            @PathVariable long id,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        return liveTests.approve(id, body, actor(authentication));
    }

    private String actor(Authentication authentication) {
        return authentication == null ? "" : authentication.getName();
    }
}
