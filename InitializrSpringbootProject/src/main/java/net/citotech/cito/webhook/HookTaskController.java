package net.citotech.cito.webhook;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v2/admin/webhooks")
public class HookTaskController {
    private final HookTaskService hookTaskService;

    public HookTaskController(HookTaskService hookTaskService) {
        this.hookTaskService = hookTaskService;
    }

    @PostMapping(path = "/process-due")
    public ResponseEntity<Map<String, Object>> processDue(@RequestParam(value = "limit", defaultValue = "50") int limit) {
        int processed = hookTaskService.processDue(limit);
        Map<String, Object> response = new HashMap<>();
        response.put("processed", processed);
        return ResponseEntity.ok(response);
    }
}
