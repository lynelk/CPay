package net.citotech.cito.reconciliation;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class ReconService {
    private final ReconciliationRepository repository;
    private final ProviderStatementParserRegistry parserRegistry;

    public ReconService(
            ReconciliationRepository repository, ProviderStatementParserRegistry parserRegistry) {
        this.repository = repository;
        this.parserRegistry = parserRegistry;
    }

    public long importStatement(
            String providerCode, String fileName, String importedBy, byte[] content) {
        ProviderStatementParser parser = parserRegistry.get(providerCode);
        List<StatementRow> rows = parser.parse(content, fileName);
        long importId =
                repository.createImport(
                        parser.providerCode(),
                        parser.channelCode(),
                        fileName,
                        importedBy,
                        rows.size());
        for (StatementRow row : rows) {
            repository.insertStatementRow(importId, row);
        }
        autoMatch();
        return importId;
    }

    public int autoMatch() {
        return repository.autoMatchByMerchantReference();
    }

    public List<ReconciliationRecord> unmatched(int limit) {
        return repository.findUnmatched(limit);
    }

    public void approveMatch(long recordId, String transactionId, String reason) {
        repository.markOperatorMatch(
                recordId, transactionId, reason == null ? "operator-approved" : reason);
    }

    /**
     * Audit O2: candidate-transaction search for the manual-match workbench. Requires at least one
     * of reference/amount/from/to so an operator can't accidentally trigger an unbounded scan of
     * the transaction log from an empty search box.
     */
    public List<CandidateTransaction> candidateTransactions(
            String reference,
            BigDecimal amount,
            String currency,
            LocalDate from,
            LocalDate to,
            int limit) {
        if (!StringUtils.hasText(reference) && amount == null && from == null && to == null) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return repository.findCandidateTransactions(
                reference, amount, currency, from, to, safeLimit);
    }
}
