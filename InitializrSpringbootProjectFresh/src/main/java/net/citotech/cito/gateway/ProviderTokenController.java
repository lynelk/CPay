package net.citotech.cito.gateway;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v2/admin/provider-tokens")
@PreAuthorize("hasRole('ADMIN')")
public class ProviderTokenController {
    private final ProviderTokenStoreService tokenStoreService;

    public ProviderTokenController(ProviderTokenStoreService tokenStoreService) {
        this.tokenStoreService = tokenStoreService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public String save(@RequestBody ProviderTokenSaveRequest request) {
        String environment = blank(request.environment()) ? "PRODUCTION" : request.environment();
        tokenStoreService.save(
                request.provider(),
                request.segment(),
                environment,
                request.token(),
                parse(request.expiresAt()));
        return "saved";
    }

    @PostMapping(path = "/lease")
    public String lease(
            @RequestParam("provider") String provider,
            @RequestParam("segment") String segment,
            @RequestParam(value = "environment", defaultValue = "PRODUCTION") String environment,
            @RequestParam("owner") String owner) {
        boolean leased =
                tokenStoreService.acquireRefreshLease(
                        provider,
                        segment,
                        environment,
                        owner,
                        Instant.now().plus(2, ChronoUnit.MINUTES));
        return "leased=" + leased;
    }

    @DeleteMapping(path = "/expired")
    public String deleteExpired() {
        return "deleted=" + tokenStoreService.deleteExpired();
    }

    private Instant parse(String expiresAt) {
        return expiresAt == null || expiresAt.trim().isEmpty() ? null : Instant.parse(expiresAt);
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public record ProviderTokenSaveRequest(
            String provider, String segment, String environment, String token, String expiresAt) {}
}
