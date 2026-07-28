package net.citotech.cito.reconciliation;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ReconciliationReviewService {
    private final ReconciliationReviewRepository repository;

    public ReconciliationReviewService(ReconciliationReviewRepository repository) {
        this.repository = repository;
    }

    public void request(long recordId, String transactionId, String type, BigDecimal amount, String currency, String reason, String requestedBy) {
        repository.request(recordId, transactionId, type, amount, currency, reason, requestedBy);
    }

    public List<ReconciliationReview> pending(int limit) {
        return repository.pending(limit);
    }

    public void approve(long id, String reviewedBy, String note) {
        repository.decide(id, "APPROVED", reviewedBy, note);
    }

    public void reject(long id, String reviewedBy, String note) {
        repository.decide(id, "REJECTED", reviewedBy, note);
    }
}

