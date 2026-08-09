package net.citotech.cito.communication.sms;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin admin trigger for the extracted SMS pending-send delivery worker (ISO domain mapping:
 * communication/sms, B1 "thin controllers"). This is the first-class replacement path for the
 * legacy {@code POST /transactions/testSendPendingSmsCron} endpoint: it delegates the entire
 * bill/send/reverse/update loop to {@link SmsDeliveryService} instead of carrying the logic inline.
 * The remote-trigger path remains ShedLock-serialized at the worker/scheduler level, so this HTTP
 * trigger and the opt-in {@link SmsDeliveryScheduler} can both run in HA safely.
 */
@RestController
@RequestMapping(path = "/api/v2/admin/communication/sms")
@PreAuthorize("hasRole('ADMIN')")
public class SmsAdminController {

    private final SmsDeliveryService smsDeliveryService;

    public SmsAdminController(SmsDeliveryService smsDeliveryService) {
        this.smsDeliveryService = smsDeliveryService;
    }

    /**
     * Kick the pending-send sweep on demand. Mirrors the legacy {@code testSendPendingSmsCron}
     * semantics: process up to {@code limit} PENDING merchant_sms rows and report how many were
     * dispatched. Errors are surfaced rather than swallowed, unlike the operator-test legacy
     * endpoint, so an admin sees a failed gate configuration immediately.
     */
    @PostMapping(path = "/deliver-due")
    public Map<String, Object> deliverDue(
            @RequestParam(value = "limit", defaultValue = "1000") int limit) {
        int processed = smsDeliveryService.deliverDue(limit);
        return Map.of("code", "000", "processed", processed);
    }
}
