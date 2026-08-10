package net.citotech.cito.transactions;

import net.citotech.cito.Common;
import net.citotech.cito.Model.Statement;
import net.citotech.cito.Model.Transaction;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Command boundary for legacy transaction resolution and statement mutations.
 *
 * <p>This is intentionally separate from {@link TransactionQueryService}: reads can evolve without
 * acquiring money-moving dependencies, while status resolution and statement writes remain behind
 * one explicit command seam. The underlying compatibility implementation remains in {@link Common}
 * until the v1 contract suite proves the final body move is behavior-neutral.
 */
@Service
public class TransactionResolutionService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;

    public TransactionResolutionService(
            NamedParameterJdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionManager = transactionManager;
    }

    /** Resolve/check one transaction and atomically apply the corresponding legacy ledger effects. */
    public String update(Transaction transaction) {
        return Common.updateTx(transaction, jdbcTemplate, transactionManager);
    }

    /** Post one compatibility statement mutation using the shared Common balance engine. */
    public String recordStatement(Statement statement, String balanceType) {
        return Common.recordStatementTx(statement, balanceType, jdbcTemplate, transactionManager);
    }
}
