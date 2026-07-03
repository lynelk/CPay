package net.citotech.cito.portal;

import java.util.List;
import net.citotech.cito.PaymentOrchestrationService;
import net.citotech.cito.api.v2.dto.PaymentChannelResponse;
import net.citotech.cito.webhook.HookTaskService;
import net.citotech.cito.reconciliation.ReconService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v2/admin/gateways")
public class GatewayAdminController {
    private final PaymentOrchestrationService paymentOrchestrationService;
    private final HookTaskService hookTaskService;
    private final ReconService reconService;

    public GatewayAdminController(PaymentOrchestrationService paymentOrchestrationService,
                                  HookTaskService hookTaskService,
                                  ReconService reconService) {
        this.paymentOrchestrationService = paymentOrchestrationService;
        this.hookTaskService = hookTaskService;
        this.reconService = reconService;
    }

    @GetMapping(path = "/channels")
    public List<PaymentChannelResponse> channels() {
        return paymentOrchestrationService.listChannels();
    }

    @PostMapping(path = "/webhooks/process-due")
    public ResponseEntity<String> processWebhooks(@RequestParam(value = "limit", defaultValue = "50") int limit) {
        int processed = hookTaskService.processDue(limit);
        return ResponseEntity.ok("processed=" + processed);
    }

    @PostMapping(path = "/reconciliation/auto-match")
    public ResponseEntity<String> autoMatch() {
        int matched = reconService.autoMatch();
        return ResponseEntity.ok("matched=" + matched);
    }
}
