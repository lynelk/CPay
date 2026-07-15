package net.citotech.cito.gateway;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v2/admin/provider-sandbox")
public class SandboxRunController {
    private final SandboxRunService service;

    public SandboxRunController(SandboxRunService service) {
        this.service = service;
    }

    @PostMapping(path = "/run")
    public long run(@RequestParam("channel") String channel,
                    @RequestParam(value = "scenario", defaultValue = "collect") String scenario,
                    @RequestParam(value = "account", defaultValue = "256770000000") String account,
                    @RequestParam(value = "amount", defaultValue = "1000") String amount) {
        return service.run(channel, scenario, account, amount);
    }
}

