package net.citotech.cito.billing.reconciliation;

import java.time.Instant;
import java.util.List;

/**
 * Result of comparing {@code merchant_transactions_log} against {@code billing_usage_events} for a
 * trailing window (the Phase 1 exit criterion: payments are 1:1 events, zero tolerance).
 */
public record UsageReconciliationResult(
        Instant windowStart,
        Instant windowEnd,
        long usageEventCount,
        long transactionLogCount,
        List<String> missingFromUsageEvents,
        List<String> missingFromTransactionLog) {

    public boolean isFullyReconciled() {
        return missingFromUsageEvents.isEmpty() && missingFromTransactionLog.isEmpty();
    }
}
