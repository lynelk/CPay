package net.citotech.cito.payments.legacy;

import net.citotech.cito.LegacyMoneyMovementEngine;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.Model.Transaction;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Compatibility seam around the extracted legacy money engine.
 *
 * <p>Modern orchestration depends on this service rather than the Common compatibility facade.
 * Raw v1 callers may continue using Common.doPayIn/doPayOut, whose public signatures now delegate
 * to the same {@link LegacyMoneyMovementEngine}. That keeps one execution implementation while the
 * old API surface remains stable.
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
}
