package net.citotech.cito.callback;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Audit E3: method-level reinforcement of the /api/v2/admin/** -> hasRole("ADMIN") rule already
// enforced by SecurityConfig's filterChain (defense in depth, not a replacement for it). This
// controller rotates merchant callback secrets and requeues parked callback tasks, so it is one
// of the clearest "sensitive admin action" candidates for this hardening.
@RestController
@RequestMapping(path = "/api/v2/admin/callback-admin")
@PreAuthorize("hasRole('ADMIN')")
public class CallbackAdminController {
    private final CallbackAdminService service;

    public CallbackAdminController(CallbackAdminService service) {
        this.service = service;
    }

    @PostMapping(path = "/rotate")
    public String rotate(@RequestParam("merchantId") long merchantId,
                         @RequestParam(value = "alias", defaultValue = "default") String alias) {
        return service.rotateSecret(merchantId, alias);
    }

    @PostMapping(path = "/retry-task")
    public String retryTask(@RequestParam("taskId") long taskId) {
        return "updated=" + service.requeueParked(taskId);
    }

    @PostMapping(path = "/retry-merchant")
    public String retryMerchant(@RequestParam("merchantId") long merchantId) {
        return "updated=" + service.requeueMerchant(merchantId);
    }
}

