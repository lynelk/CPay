package net.citotech.cito.communication.ussd;

import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only operations view of recent USSD sessions. */
@RestController
@RequestMapping(path = "/api/v2/admin/communication/ussd")
@PreAuthorize("hasRole('ADMIN')")
public class UssdAdminController {
    private final UssdSessionService sessionService;

    public UssdAdminController(UssdSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping(path = "/sessions")
    public List<Map<String, Object>> sessions(
            @RequestParam(value = "merchantId", required = false) Long merchantId) {
        return sessionService.recentSessions(merchantId);
    }
}
