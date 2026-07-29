package net.citotech.cito.compliance;

import net.citotech.cito.Model.Merchant;
import net.citotech.cito.api.v2.dto.PaymentRequest;
import org.springframework.stereotype.Component;

/**
 * Static bridge so {@code Common.doPayIn}/{@code doPayOut} (static-utility methods, not Spring
 * beans) can run the same risk/fraud authorization the v2 orchestration path already runs (audit
 * I1) - previously the legacy path never called RiskDecisionService at all, so a legacy request
 * could bypass blocklist, sanctions, single-transaction-cap, and daily-cap checks entirely just by
 * using {@code /api/v1/doMobileMoney*} instead of the v2 API. Same pattern as the other static
 * registries ({@code ProviderTokenStoreRegistry}, {@code PayoutCompensationSagaRegistry}, etc.) -
 * a no-op (fail-open) only when the registry hasn't been wired by Spring, which in practice means
 * an uninitialized test context, not a real deployment.
 */
@Component
public class RiskDecisionRegistry {
    private static volatile RiskDecisionService riskDecisionService;

    public RiskDecisionRegistry(RiskDecisionService riskDecisionService) {
        RiskDecisionRegistry.riskDecisionService = riskDecisionService;
    }

    public static void authorize(Merchant merchant, PaymentRequest request, String direction) {
        RiskDecisionService service = riskDecisionService;
        if (service != null) {
            service.authorizePayment(merchant, request, direction);
        }
    }
}
