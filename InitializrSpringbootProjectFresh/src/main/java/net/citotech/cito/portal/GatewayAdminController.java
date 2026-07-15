package net.citotech.cito.portal;

import java.util.List;
import net.citotech.cito.PaymentOrchestrationService;
import net.citotech.cito.api.v2.dto.PaymentChannelResponse;
import net.citotech.cito.callback.CallbackTaskService;
import net.citotech.cito.reconciliation.ReconService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v2/admin/gateways")
public class GatewayAdminController {
    private final PaymentOrchestrationService paymentOrchestrationService;
    private final CallbackTaskService callbackTaskService;
    private final ReconService reconService;

    public GatewayAdminController(PaymentOrchestrationService paymentOrchestrationService,
                                  CallbackTaskService callbackTaskService,
                                  ReconService reconService) {
        this.paymentOrchestrationService = paymentOrchestrationService;
        this.callbackTaskService = callbackTaskService;
        this.reconService = reconService;
    }

    @GetMapping(path = "/channels")
    public List<PaymentChannelResponse> channels() {
        return paymentOrchestrationService.listChannels();
    }

    @PostMapping(path = "/callbacks/run-due")
    public String runCallbacks(@RequestParam(value = "limit", defaultValue = "50") int limit) {
        return "processed=" + callbackTaskService.processDue(limit);
    }

    @PostMapping(path = "/reconciliation/auto-match")
    public String autoMatch() {
        return "matched=" + reconService.autoMatch();
    }
}

