package net.citotech.cito.compliance;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v2/admin/compliance")
public class ComplianceReportingController {
    private final ComplianceReportingService service;

    public ComplianceReportingController(ComplianceReportingService service) {
        this.service = service;
    }

    @GetMapping(path = "/summary")
    public Map<String, Object> summary() {
        return service.summary();
    }

    @GetMapping(path = "/report")
    public Map<String, Object> report(@RequestParam(name = "from", required = false) String from,
                                      @RequestParam(name = "to", required = false) String to) {
        return service.report(from, to);
    }

    @PostMapping(path = "/events/{id}/review")
    public ResponseEntity<?> review(@PathVariable("id") long id,
                                    @RequestParam(name = "reviewedBy", required = false) String reviewedBy) {
        int updated = service.markReviewed(id, reviewedBy);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("updated", updated);
        response.put("id", id);
        return ResponseEntity.ok(response);
    }
}

