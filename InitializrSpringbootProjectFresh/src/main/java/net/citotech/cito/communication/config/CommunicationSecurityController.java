package net.citotech.cito.communication.config;

import java.util.List;
import java.util.Map;
import net.citotech.cito.communication.config.ProviderPolicyService.PolicyRow;
import net.citotech.cito.communication.credentials.CommunicationCredentialStore;
import net.citotech.cito.communication.credentials.CommunicationCredentialStore.CredentialRow;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin surface for the V54 security controls (ISO domain mapping: communication/config +
 * integration/credential, track B6): provider credentials stored encrypted at rest (ISO/IEC 27001
 * A.8.24) and per-provider rate-limit/timeout policies (ISO/IEC 27001 A.8.6). Credential writes
 * accept the plaintext value once and store only the AES-GCM envelope; reads return masked values
 * only, so the UI never round-trips a provider secret.
 */
@RestController
@RequestMapping(path = "/api/v2/admin/communication/security")
@PreAuthorize("hasRole('ADMIN')")
public class CommunicationSecurityController {

    private final CommunicationCredentialStore credentialStore;
    private final ProviderPolicyService policyService;

    public CommunicationSecurityController(
            CommunicationCredentialStore credentialStore, ProviderPolicyService policyService) {
        this.credentialStore = credentialStore;
        this.policyService = policyService;
    }

    @GetMapping(path = "/credentials/{providerCode}")
    public Map<String, Object> credentials(@PathVariable String providerCode) {
        List<CredentialRow> rows = credentialStore.listForProvider(providerCode.toUpperCase());
        return Map.of(
                "code", "000", "providerCode", providerCode.toUpperCase(), "credentials", rows);
    }

    @PostMapping(path = "/credentials")
    public Map<String, Object> saveCredential(@RequestBody CredentialRequest request) {
        CredentialRow saved =
                credentialStore.save(
                        request.providerCode(), request.credentialKey(), request.value());
        return Map.of("code", "000", "credential", saved);
    }

    @DeleteMapping(path = "/credentials/{providerCode}/{credentialKey}")
    public Map<String, Object> deleteCredential(
            @PathVariable String providerCode, @PathVariable String credentialKey) {
        int deleted = credentialStore.delete(providerCode.toUpperCase(), credentialKey);
        return Map.of("code", "000", "deleted", deleted);
    }

    @GetMapping(path = "/policies")
    public Map<String, Object> policies() {
        List<PolicyRow> rows = policyService.list();
        return Map.of("code", "000", "policies", rows);
    }

    @PostMapping(path = "/policies")
    public Map<String, Object> savePolicy(@RequestBody PolicyRequest request) {
        PolicyRow saved =
                policyService.save(
                        request.providerCode(),
                        request.maxPerMinute(),
                        request.maxPerHour(),
                        request.connectTimeoutMs(),
                        request.readTimeoutMs(),
                        request.rateLimitEnabled());
        return Map.of("code", "000", "policy", saved);
    }

    public record CredentialRequest(String providerCode, String credentialKey, String value) {}

    public record PolicyRequest(
            String providerCode,
            Integer maxPerMinute,
            Integer maxPerHour,
            Integer connectTimeoutMs,
            Integer readTimeoutMs,
            Boolean rateLimitEnabled) {}
}
