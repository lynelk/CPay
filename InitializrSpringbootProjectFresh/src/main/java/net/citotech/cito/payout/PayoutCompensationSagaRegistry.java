package net.citotech.cito.payout;

import org.springframework.stereotype.Component;

/**
 * Static bridge so {@code Common.doPayOut} (a static-utility method, not a Spring bean) can drive
 * the persisted compensation saga (audit B3). Same pattern as the other static registries
 * ({@code ProviderTokenStoreRegistry}, {@code MerchantKeyCryptoRegistry}, etc.).
 */
@Component
public class PayoutCompensationSagaRegistry {
    private static volatile PayoutCompensationSagaService sagaService;

    public PayoutCompensationSagaRegistry(PayoutCompensationSagaService sagaService) {
        PayoutCompensationSagaRegistry.sagaService = sagaService;
    }

    public static Long start(long transactionsLogId, String txUniqueId, long merchantId, int totalSteps) {
        PayoutCompensationSagaService service = sagaService;
        if (service == null) {
            return null;
        }
        return service.start(transactionsLogId, txUniqueId, merchantId, totalSteps);
    }

    public static void recordStepComplete(Long sagaId, String stepName) {
        PayoutCompensationSagaService service = sagaService;
        if (service != null && sagaId != null) {
            service.recordStepComplete(sagaId, stepName);
        }
    }

    public static void complete(Long sagaId) {
        PayoutCompensationSagaService service = sagaService;
        if (service != null && sagaId != null) {
            service.complete(sagaId);
        }
    }

    public static void markStuck(Long sagaId, String error) {
        PayoutCompensationSagaService service = sagaService;
        if (service != null && sagaId != null) {
            service.markStuck(sagaId, error);
        }
    }
}
