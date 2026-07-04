package net.citotech.cito.reconciliation;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ReconService {
    private final ReconciliationRepository repository;
    private final ProviderStatementParserRegistry parserRegistry;

    public ReconService(ReconciliationRepository repository, ProviderStatementParserRegistry parserRegistry) {
        this.repository = repository;
        this.parserRegistry = parserRegistry;
    }

    public long importStatement(String providerCode, String fileName, String importedBy, String csvText) {
        ProviderStatementParser parser = parserRegistry.get(providerCode);
        List<StatementRow> rows = parser.parse(csvText);
        long importId = repository.createImport(parser.providerCode(), parser.channelCode(), fileName, importedBy, rows.size());
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
        repository.markOperatorMatch(recordId, transactionId, reason == null ? "operator-approved" : reason);
    }
}
