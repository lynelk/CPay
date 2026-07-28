package net.citotech.cito.gateway;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v2/admin/provider-certification")
public class ProviderCertificationController {
    private final ProviderCertificationService service;

    public ProviderCertificationController(ProviderCertificationService service) {
        this.service = service;
    }

    @GetMapping(path = "/summary")
    public Map<String, Object> summary() {
        return service.summary();
    }

    @GetMapping(path = "/evidence")
    public List<Map<String, Object>> evidence(
            @RequestParam(name = "provider", required = false) String provider,
            @RequestParam(name = "channel", required = false) String channel) {
        return service.listEvidence(provider, channel);
    }

    @PostMapping(path = "/evidence/{id}/approve")
    public ResponseEntity<?> approve(
            @PathVariable("id") long id,
            @RequestParam(name = "approvedBy", required = false) String approvedBy) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", id);
        response.put("updated", service.approveEvidence(id, approvedBy));
        return ResponseEntity.ok(response);
    }
}
