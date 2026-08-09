package net.citotech.cito.communication.routing;

import java.util.Map;
import java.util.Optional;
import net.citotech.cito.communication.routing.CommunicationRoutingRepository.ProviderRow;
import net.citotech.cito.communication.routing.CommunicationRoutingRepository.RuleRow;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin admin surface for communication provider routing (ISO domain mapping: communication/routing,
 * track B1a). Backs the {@code ModuleCommunicationRouting} admin screen: browse the provider
 * catalog, list/upsert/delete routing rules, and preview which adapter a merchant+channel resolves
 * to today. Writes are immediate — the router reads these tables on every send, so a saved rule
 * takes effect on the next pending-send sweep with no restart.
 */
@RestController
@RequestMapping(path = "/api/v2/admin/communication/routing")
@PreAuthorize("hasRole('ADMIN')")
public class CommunicationRoutingController {

    private final CommunicationRoutingRepository repository;

    public CommunicationRoutingController(CommunicationRoutingRepository repository) {
        this.repository = repository;
    }

    @GetMapping(path = "/providers")
    public Map<String, Object> providers() {
        return Map.of("code", "000", "providers", repository.providers());
    }

    @GetMapping(path = "/rules")
    public Map<String, Object> rules() {
        return Map.of("code", "000", "rules", repository.rules());
    }

    /**
     * Preview which rule and provider a merchant+channel would use on the next send. merchantId
     * optional; omitted resolves the platform default.
     */
    @GetMapping(path = "/effective")
    public Map<String, Object> effective(
            @RequestParam(value = "merchantId", required = false) Long merchantId,
            @RequestParam(value = "channel", defaultValue = "SMS") String channel) {
        Optional<RuleRow> rule = repository.effectiveRule(channel, merchantId);
        if (rule.isEmpty()) {
            return Map.of("code", "000", "resolved", false);
        }
        RuleRow row = rule.get();
        ProviderRow provider =
                repository.provider(row.providerCode(), channel).orElse(null);
        return Map.of(
                "code", "000",
                "resolved", true,
                "rule", row,
                "provider", provider);
    }

    @PostMapping(path = "/rules")
    public Map<String, Object> upsertRule(@RequestBody RuleUpsertRequest request) {
        RuleRow saved =
                repository.upsertRule(
                        request.id(),
                        request.channel() == null || request.channel().isBlank()
                                ? "SMS"
                                : request.channel(),
                        request.merchantId(),
                        request.priority() == null ? 100 : request.priority(),
                        request.providerCode(),
                        request.enabledFlag() == null || request.enabledFlag().isBlank()
                                ? "YES"
                                : request.enabledFlag().toUpperCase());
        return Map.of("code", "000", "rule", saved);
    }

    @DeleteMapping(path = "/rules/{ruleId}")
    public Map<String, Object> deleteRule(@PathVariable long ruleId) {
        repository.deleteRule(ruleId);
        return Map.of("code", "000", "deleted", ruleId);
    }

    /** Upsert payload; id null inserts (or reuses the row for the channel+merchant scope). */
    public record RuleUpsertRequest(
            Long id,
            String channel,
            Long merchantId,
            Integer priority,
            String providerCode,
            String enabledFlag) {}
}
