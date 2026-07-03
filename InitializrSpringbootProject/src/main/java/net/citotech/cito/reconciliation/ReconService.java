package net.citotech.cito.reconciliation;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ReconService {
    private final ReconciliationRepository repository;

    public ReconService(ReconciliationRepository repository) {
        this.repository = repository;
    }

    public int autoMatch() {
        return repository.autoMatchByMerchantReference();
    }

    public List<ReconciliationRecord> unmatched(int limit) {
        return repository.findUnmatched(limit);
    }

    public void approveMatch(long recordId, String transactionId, String reason) {
        repository.markManualMatch(recordId, transactionId, reason == null ? "operator-approved" : reason);
    }
}
