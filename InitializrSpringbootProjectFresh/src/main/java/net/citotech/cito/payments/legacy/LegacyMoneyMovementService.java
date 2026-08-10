package net.citotech.cito.payments.legacy;

import net.citotech.cito.Common;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.Model.Transaction;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Compatibility seam around the legacy money engine.
 *
 * <p>New payment orchestration must depend on this service rather than calling the Common god class
 * directly. The first slice deliberately delegates without changing behavior. Subsequent slices can
 * move the implementation of doPayIn/doPayOut out of Common behind this stable boundary while the
 * v1 response contract remains untouched.
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
        return Common.doPayIn(transaction, merchant, jdbcTemplate, transactionManager, true);
    }

    /** Executes after the caller has already completed risk authorization. */
    public String payout(Transaction transaction, Merchant merchant) {
        return Common.doPayOut(transaction, merchant, jdbcTemplate, transactionManager, true);
    }
}
