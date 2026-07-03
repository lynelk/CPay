package net.citotech.cito.api.v2;

import net.citotech.cito.api.v2.dto.V2HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v2")
public class V2HealthController {
    @GetMapping(path = "/health")
    public V2HealthResponse health() {
        return new V2HealthResponse("UP", "v2");
    }
}
