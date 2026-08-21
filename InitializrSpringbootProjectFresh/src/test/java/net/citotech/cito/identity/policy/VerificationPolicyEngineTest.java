package net.citotech.cito.identity.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import net.citotech.cito.identity.domain.CheckOutcome;
import net.citotech.cito.identity.domain.ValidationCapability;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for the CPay decision boundary (Track B Phase 7). The guide's core invariant:
 * a technical failure (ERROR) or ambiguous state (PENDING / missing check) must never become an
 * identity rejection — it must surface as INCONCLUSIVE. Business failures are distinct: a
 * critical FAIL rejects, a non-critical FAIL routes to manual review.
 */
class VerificationPolicyEngineTest {

    private static final String POLICY = "CUSTOMER_BASIC_KYC";
    private static final int VERSION = 1;

    private VerificationPolicyEngine engine() {
        return new VerificationPolicyEngine(
                new VerificationPolicyEngine.PolicyDatabase()
                        .register(
                                new VerificationPolicyEngine.PolicyDatabase.Policy(
                                        POLICY,
                                        VERSION,
                                        Set.of(ValidationCapability.NIN, ValidationCapability.PHONE_OWNERSHIP),
                                        Set.of(ValidationCapability.NIN))));
    }

    private Map<ValidationCapability, CheckOutcome> outcomes(CheckOutcome nin, CheckOutcome phone) {
        Map<ValidationCapability, CheckOutcome> executed = new EnumMap<>(ValidationCapability.class);
        executed.put(ValidationCapability.NIN, nin);
        executed.put(ValidationCapability.PHONE_OWNERSHIP, phone);
        return executed;
    }

    @Test
    void allRequiredChecksPassingVerifies() {
        VerificationPolicyEngine.Decision decision =
                engine().decide(POLICY, VERSION, outcomes(CheckOutcome.PASS, CheckOutcome.PASS));

        assertThat(decision.result()).isEqualTo("VERIFIED");
        assertThat(decision.reasonCode()).isEqualTo("ALL_REQUIRED_CHECKS_PASSED");
    }

    @Test
    void providerTechnicalErrorIsInconclusiveNeverRejected() {
        // The guide's central rule: gnuGrid OAuth failure / timeout / connection problem must not
        // silently become "NIN invalid" or "customer rejected".
        VerificationPolicyEngine.Decision decision =
                engine().decide(POLICY, VERSION, outcomes(CheckOutcome.ERROR, CheckOutcome.PASS));

        assertThat(decision.result()).isEqualTo("INCONCLUSIVE");
        assertThat(decision.reasonCode())
                .isEqualTo("PROVIDER_TECHNICAL_FAILURE:" + ValidationCapability.NIN);
    }

    @Test
    void pendingCheckIsInconclusive() {
        VerificationPolicyEngine.Decision decision =
                engine().decide(POLICY, VERSION, outcomes(CheckOutcome.PASS, CheckOutcome.PENDING));

        assertThat(decision.result()).isEqualTo("INCONCLUSIVE");
        assertThat(decision.reasonCode())
                .isEqualTo("MISSING_OR_PENDING_CHECK:" + ValidationCapability.PHONE_OWNERSHIP);
    }

    @Test
    void missingCheckIsInconclusive() {
        // Only one of two required checks executed — incompleteness is not failure.
        VerificationPolicyEngine.Decision decision =
                engine()
                        .decide(
                                POLICY,
                                VERSION,
                                Map.of(ValidationCapability.NIN, CheckOutcome.PASS));

        assertThat(decision.result()).isEqualTo("INCONCLUSIVE");
        assertThat(decision.reasonCode())
                .isEqualTo("MISSING_OR_PENDING_CHECK:" + ValidationCapability.PHONE_OWNERSHIP);
    }

    @Test
    void criticalCheckFailureRejects() {
        VerificationPolicyEngine.Decision decision =
                engine().decide(POLICY, VERSION, outcomes(CheckOutcome.FAIL, CheckOutcome.PASS));

        assertThat(decision.result()).isEqualTo("REJECTED");
        assertThat(decision.reasonCode())
                .isEqualTo("CRITICAL_CHECK_FAILED:" + ValidationCapability.NIN);
    }

    @Test
    void nonCriticalCheckFailureRoutesToManualReview() {
        VerificationPolicyEngine.Decision decision =
                engine().decide(POLICY, VERSION, outcomes(CheckOutcome.PASS, CheckOutcome.FAIL));

        assertThat(decision.result()).isEqualTo("REVIEW_REQUIRED");
        assertThat(decision.reasonCode())
                .isEqualTo("NON_CRITICAL_CHECK_FAILED:" + ValidationCapability.PHONE_OWNERSHIP);
    }

    @Test
    void unknownPolicyFailsClosed() {
        // An unregistered policy id/version must never fabricate a pass — fail closed with
        // INCONCLUSIVE and the guide's VALIDATION_POLICY_NOT_AVAILABLE reason code.
        VerificationPolicyEngine.Decision decision =
                engine().decide("NO_SUCH_POLICY", 99, Map.of());

        assertThat(decision.result()).isEqualTo("INCONCLUSIVE");
        assertThat(decision.reasonCode()).isEqualTo("VALIDATION_POLICY_NOT_AVAILABLE");
    }

    @Test
    void criticalSubsetOfRequiredIsEnforced() {
        assertThatThrownBy(
                        () ->
                                new VerificationPolicyEngine.PolicyDatabase.Policy(
                                        "BAD",
                                        1,
                                        Set.of(ValidationCapability.NIN),
                                        Set.of(ValidationCapability.NIN, ValidationCapability.KYC_REPORT)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
