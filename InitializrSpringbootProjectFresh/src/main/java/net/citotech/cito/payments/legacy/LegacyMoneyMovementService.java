package net.citotech.cito.payments.legacy;

import net.citotech.cito.LegacyMoneyMovementEngine;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.Model.Transaction;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Application seam around the extracted legacy money engine.
 *
 * <p>Modern orchestration uses {@link #collect} / {@link #payout} after it has already authorized
 * risk. Legacy portal flows use the {@code WithRisk} variants so they retain the raw-v1 behavior of
 * authorizing inside the compatibility engine. Raw API v1 callers may continue using the Common
 * facade; all paths execute the same physical engine.
 */
@Service
public class LegacyMoneyMovementService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;

    public LegacyMoneyMovementService(
            NamedParameterJdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionManager = transactionManager;
    }

    /** Executes after the caller has already completed risk authorization. */
    public String collect(Transaction transaction, Merchant merchant) {
        return LegacyMoneyMovementEngine.doPayIn(
                transaction, merchant, jdbcTemplate, transactionManager, true);
    }

    /** Executes after the caller has already completed risk authorization. */
    public String payout(Transaction transaction, Merchant merchant) {
        return LegacyMoneyMovementEngine.doPayOut(
                transaction, merchant, jdbcTemplate, transactionManager, true);
    }

    /** Legacy portal/v1-style collection: the engine owns risk authorization. */
    public String collectWithRisk(Transaction transaction, Merchant merchant) {
        return LegacyMoneyMovementEngine.doPayIn(
                transaction, merchant, jdbcTemplate, transactionManager, false);
    }

    /** Legacy portal/v1-style payout: the engine owns risk authorization. */
    public String payoutWithRisk(Transaction transaction, Merchant merchant) {
        return LegacyMoneyMovementEngine.doPayOut(
                transaction, merchant, jdbcTemplate, transactionManager, false);
    }
}
