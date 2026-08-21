package net.citotech.cito.identity.policy;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.citotech.cito.identity.domain.CheckOutcome;
import net.citotech.cito.identity.domain.ValidationCapability;

/**
 * Deterministic, versioned verification policy engine (Track B Phase 7). A policy is a set of
 * required checks plus expected outcomes; the final decision is derived from the executed check
 * outcomes, never from raw provider responses. This is the CPay decision boundary: provider
 * evidence (from adapters) is consumed here and turned into a CPay-compliant result.
 *
 * <p>Decision rules: any {@code ERROR} (technical failure) or {@code PENDING} (ambiguous) makes
 * the case {@code INCONCLUSIVE} — a provider outage must never become an identity rejection.
 * Any required check that {@code FAIL}ed makes the case {@code REJECTED} when the check is
 * critical, otherwise {@code REVIEW_REQUIRED}. All required checks passing — {@code VERIFIED}.
 */
public class VerificationPolicyEngine {

    private final PolicyDatabase policies;

    public VerificationPolicyEngine(PolicyDatabase policies) {
        this.policies = policies;
    }

    /** The checks a policy requires for {@code policyId}@{@code version}. */
    public Set<ValidationCapability> requiredChecks(String policyId, int version) {
        return Set.copyOf(policies.required(policyId, version));
    }

    /**
     * Produces the final CPay decision from executed check outcomes. {@code executed} may contain
     * only a subset of the policy's required checks (e.g. when a technical error interrupted a
     * cascade) — missing required checks are evidence of incompleteness, not of a verification
     * failure or success.
     */
    public Decision decide(
            String policyId, int version, Map<ValidationCapability, CheckOutcome> executed) {
        // Fail closed: an unregistered policy id/version must never fabricate a pass.
        if (!policies.isRegistered(policyId, version)) {
            return Decision.INCONCLUSIVE("VALIDATION_POLICY_NOT_AVAILABLE");
        }
        Set<ValidationCapability> required = policies.required(policyId, version);
        Set<ValidationCapability> critical = policies.critical(policyId, version);
        Map<ValidationCapability, CheckOutcome> outcomes =
                new EnumMap<>(ValidationCapability.class);
        outcomes.putAll(executed == null ? Map.of() : executed);

        for (ValidationCapability capability : required) {
            CheckOutcome outcome = outcomes.get(capability);
            if (outcome == null || outcome == CheckOutcome.PENDING) {
                return Decision.INCONCLUSIVE("MISSING_OR_PENDING_CHECK:" + capability);
            }
            if (outcome == CheckOutcome.ERROR) {
                return Decision.INCONCLUSIVE("PROVIDER_TECHNICAL_FAILURE:" + capability);
            }
        }
        for (ValidationCapability capability : required) {
            if (outcomes.get(capability) == CheckOutcome.FAIL) {
                return critical.contains(capability)
                        ? Decision.REJECTED("CRITICAL_CHECK_FAILED:" + capability)
                        : Decision.REVIEW_REQUIRED("NON_CRITICAL_CHECK_FAILED:" + capability);
            }
        }
        return Decision.VERIFIED("ALL_REQUIRED_CHECKS_PASSED");
    }

    /** Versioned policy rule set (seeded from the module's initial policy catalog). */
    public static final class PolicyDatabase {

        private static final Policy MISSING = new Policy("missing", 1, Set.of(), Set.of());

        private final Map<String, Policy> byKey = new HashMap<>();

        public PolicyDatabase register(Policy policy) {
            byKey.put(policy.policyId() + "@" + policy.version(), policy);
            return this;
        }

        public Set<ValidationCapability> required(String policyId, int version) {
            return byKey.getOrDefault(policyId + "@" + version, MISSING).required();
        }

        public Set<ValidationCapability> critical(String policyId, int version) {
            return byKey.getOrDefault(policyId + "@" + version, MISSING).critical();
        }

        public boolean isRegistered(String policyId, int version) {
            return byKey.containsKey(policyId + "@" + version);
        }

        /** A policy definition. {@code critical} is a subset of {@code required}. */
        public record Policy(
                String policyId,
                int version,
                Set<ValidationCapability> required,
                Set<ValidationCapability> critical) {

            public Policy {
                required = required == null ? Set.of() : Set.copyOf(required);
                critical = critical == null ? Set.of() : Set.copyOf(critical);
                if (!required.containsAll(critical)) {
                    throw new IllegalArgumentException(
                            "critical checks must be a subset of required checks");
                }
            }
        }
    }

    /** CPay-compliant decision with a reason code — reproducible by construction. */
    public record Decision(String result, String reasonCode) {

        public static Decision VERIFIED(String reasonCode) {
            return new Decision("VERIFIED", reasonCode);
        }

        public static Decision REJECTED(String reasonCode) {
            return new Decision("REJECTED", reasonCode);
        }

        public static Decision REVIEW_REQUIRED(String reasonCode) {
            return new Decision("REVIEW_REQUIRED", reasonCode);
        }

        public static Decision INCONCLUSIVE(String reasonCode) {
            return new Decision("INCONCLUSIVE", reasonCode);
        }
    }
}
