package net.citotech.cito.ledger;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.citotech.cito.Common;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.Model.Transaction;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Conservative repair sweep for legacy money movement that predates the normalized ledger path. It
 * only backfills successful legacy pay-in/pay-out rows that have no idempotent {@code
 * payment:<tx_unique_id>} ledger transaction yet.
 */
@Service
public class LegacyLedgerRepairService {
    private static final Logger LOGGER =
            Logger.getLogger(LegacyLedgerRepairService.class.getName());

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final LegacyLedgerPostingService legacyLedgerPostingService;

    public LegacyLedgerRepairService(
            NamedParameterJdbcTemplate jdbcTemplate,
            LegacyLedgerPostingService legacyLedgerPostingService) {
        this.jdbcTemplate = jdbcTemplate;
        this.legacyLedgerPostingService = legacyLedgerPostingService;
    }

    public int repairMissingPaymentLedgerEntries(int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 500));
        List<Transaction> missing = findSuccessfulPaymentTransactionsWithoutLedger(boundedLimit);
        int repaired = 0;
        for (Transaction tx : missing) {
            try {
                Merchant merchant = Common.getMerchantById(tx.getMerchant_id(), jdbcTemplate);
                legacyLedgerPostingService.postPaymentEntries(
                        tx.getTx_type(),
                        tx.getGateway_id(),
                        merchant,
                        tx,
                        tx.getOriginal_amount(),
                        tx.getCharges());
                repaired++;
            } catch (Exception ex) {
                LOGGER.log(
                        Level.WARNING,
                        "Unable to repair missing ledger posting for transaction " + tx.getId(),
                        ex);
            }
        }
        return repaired;
    }

    private List<Transaction> findSuccessfulPaymentTransactionsWithoutLedger(int limit) {
        String sql =
                "SELECT tx.* FROM "
                        + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG
                        + " tx LEFT JOIN ledger_transactions lt "
                        + "ON lt.transaction_reference = CONCAT('payment:', tx.tx_unique_id) "
                        + "WHERE tx.status='SUCCESSFUL' "
                        + "AND tx.tx_type IN (:tx_types) "
                        + "AND tx.tx_unique_id IS NOT NULL "
                        + "AND tx.tx_unique_id <> '' "
                        + "AND lt.id IS NULL "
                        + "ORDER BY tx.id ASC LIMIT "
                        + limit;
        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue(
                                "tx_types",
                                List.of(Transaction.TX_TYPE_PAYIN, Transaction.TX_TYPE_PAYOUT));
        return jdbcTemplate.query(sql, params, Common.getTransactionRowMapper());
    }
}
