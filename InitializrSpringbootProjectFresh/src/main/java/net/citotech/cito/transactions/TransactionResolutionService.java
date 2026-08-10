package net.citotech.cito.transactions;

import net.citotech.cito.LegacyStatementEngine;
import net.citotech.cito.Model.Statement;
import net.citotech.cito.Model.Transaction;
import net.citotech.cito.TransactionResolutionEngine;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Command boundary for transaction resolution and statement mutations.
 *
 * <p>This remains separate from {@link TransactionQueryService}: query code cannot accidentally
 * acquire money-moving responsibilities. The old Common methods remain compatibility delegates for
 * v1 callers, while controller/domain code reaches the extracted engines through this service.
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

    /** Resolve/check one transaction and atomically apply settlement or reversal effects. */
    public String update(Transaction transaction) {
        return TransactionResolutionEngine.updateTx(transaction, jdbcTemplate, transactionManager);
    }

    /** Post one compatibility statement mutation through the extracted balance engine. */
    public String recordStatement(Statement statement, String balanceType) {
        return LegacyStatementEngine.recordStatementTx(
                statement, balanceType, jdbcTemplate, transactionManager);
    }
}
