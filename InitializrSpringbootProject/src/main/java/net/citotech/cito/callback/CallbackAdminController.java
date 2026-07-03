package net.citotech.cito.callback;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v2/admin/callback-admin")
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
