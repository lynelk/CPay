package net.citotech.cito.identity.provider;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import net.citotech.cito.identity.domain.CheckOutcome;

/**
 * Normalized provider result (ISO domain mapping: identity/provider). Provider responses are
 * reduced to evidence; CPay's policy engine owns the final verification decision. Technical
 * failures surface as {@link CheckOutcome#ERROR}, never as identity or credit rejection.
 */
public record ProviderCheckResult(
        CheckOutcome outcome,
        Double confidence,
        List<String> reasonCodes,
        Map<String, String> attributes,
        String externalReference,
        List<EvidenceReference> evidence,
        ProviderUsage usage) {

    public ProviderCheckResult {
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    /** Protected artifact reference — never the raw sensitive payload itself. */
    public record EvidenceReference(String evidenceType, String artifactReference) {}

    /**
     * Provider-side cost and billing metadata. {@code billableAttempt} must be {@code false} for
     * an internal technical retry that the provider contract does not charge for.
     */
    public record ProviderUsage(
            boolean billableAttempt,
            BigDecimal providerCost,
            String providerCurrency) {}
}
