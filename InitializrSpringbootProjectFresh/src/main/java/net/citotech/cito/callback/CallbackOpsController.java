package net.citotech.cito.callback;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v2/admin/callbacks")
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

