package net.citotech.cito.admin;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v2/admin/ops-dashboard")
public class OpsDashboardController {
    private final OpsAlertService service;

    public OpsDashboardController(OpsAlertService service) {
        this.service = service;
    }

    @GetMapping(path = "/summary")
    public Map<String, Object> summary() {
        return service.dashboard();
    }

    @PostMapping(path = "/alert")
    public long alert(@RequestParam("type") String type,
                      @RequestParam(value = "severity", defaultValue = "INFO") String severity,
                      @RequestParam("message") String message,
                      @RequestParam(value = "resource", required = false) String resource) {
        return service.open(type, severity, message, resource);
    }

    @PostMapping(path = "/resolve")
    public String resolve(@RequestParam("id") long id) {
        return "resolved=" + service.resolve(id);
    }
}
