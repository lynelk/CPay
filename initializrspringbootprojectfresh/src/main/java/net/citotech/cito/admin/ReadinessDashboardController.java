package net.citotech.cito.admin;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v2/admin/readiness")
public class ReadinessDashboardController {
    private final ReadinessDashboardService service;

    public ReadinessDashboardController(ReadinessDashboardService service) {
        this.service = service;
    }

    @GetMapping(path = "/summary")
    public Map<String, Object> summary() {
        return service.summary();
    }
}

