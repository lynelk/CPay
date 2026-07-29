package net.citotech.cito.callback;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Audit E3: method-level reinforcement of the /api/v2/admin/** -> hasRole("ADMIN") rule already
// enforced by SecurityConfig's filterChain (defense in depth, not a replacement for it).
@RestController
@RequestMapping(path = "/api/v2/admin/callbacks")
@PreAuthorize("hasRole('ADMIN')")
public class CallbackOpsController {
    private final CallbackTaskService service;

    public CallbackOpsController(CallbackTaskService service) {
        this.service = service;
    }

    @PostMapping(path = "/run-due")
    public ResponseEntity<Map<String, Object>> runDue(@RequestParam(value = "limit", defaultValue = "50") int limit) {
        Map<String, Object> response = new HashMap<>();
        response.put("count", service.processDue(limit));
        return ResponseEntity.ok(response);
    }
}

