package net.citotech.cito;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.citotech.cito.Model.Balance;
import net.citotech.cito.Model.GatewayChargeDetails;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.Model.Transaction;
import net.citotech.cito.ledger.DoubleEntryLedgerService;
import net.citotech.cito.money.MoneyAmount;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fail-closed guard for legacy batch payouts.
 *
 * <p>The legacy payout scheduler keeps a batch in PROCESSING when the immutable ledger correctly
 * rejects a reservation for insufficient available funds. That deterministic business condition
 * then gets retried every 30 seconds forever. This guard does not bypass or alter the ledger. It
 * pauses the batch before the provider call whenever either the legacy gateway balance or the
 * ledger-derived available balance cannot cover the next unstarted beneficiary. An operator can
 * fund/reconcile the merchant and explicitly resume the batch through the existing startPayment
 * workflow.
 */
@Component
public class BatchPayoutFundingGuard {
    private static final Logger LOG = Logger.getLogger(BatchPayoutFundingGuard.class.getName());
    private static final String DEFAULT_CURRENCY = "UGX";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final DoubleEntryLedgerService ledgerService;
    private final TransactionTemplate transactionTemplate;

    public BatchPayoutFundingGuard(
            NamedParameterJdbcTemplate jdbcTemplate,
            DoubleEntryLedgerService ledgerService,
            PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.ledgerService = ledgerService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 5000)
    @SchedulerLock(
            name = "batchPayoutFundingGuard",
            lockAtMostFor = "PT2M",
            lockAtLeastFor = "PT20S")
    public void pauseDeterministicallyUnfundedBatches() {
        for (Candidate candidate : candidates()) {
            try {
                FundingCheck check = fundingCheck(candidate);
                if (!check.funded()) {
                    pause(candidate, check);
                }
            } catch (RuntimeException ex) {
                // Guard failure must never make a healthy payout unavailable. The authoritative
                // reserve() invariant still fails closed in the payout scheduler.
                LOG.log(
                        Level.WARNING,
                        "Unable to evaluate payout funding guard for batch "
                                + candidate.batchId()
                                + ", beneficiary "
                                + candidate.beneficiaryId()
                                + ": "
                                + ex.getMessage());
            }
        }
    }

    private List<Candidate> candidates() {
        String sql =
                "SELECT p.id AS batch_id, p.merchant_id, b.id AS beneficiary_id, "
                        + "b.account, b.amount "
                        + "FROM "
                        + Common.DB_TABLE_MERCHANT_BATCH_TRANSACTION_LOG
                        + " p JOIN "
                        + Common.DB_TABLE_MERCHANT_BATCH_TRANSACTION_BENEFICIARIES
                        + " b ON b.batch_id=p.id "
                        + "WHERE p.status=:processing AND b.status=:unpaid "
                        + "AND NOT EXISTS (SELECT 1 FROM "
                        + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG
                        + " t WHERE t.merchant_batch_transactions_log_id=p.id "
                        + "AND t.beneficiary_id=b.id) "
                        + "ORDER BY p.id, b.id LIMIT 100";
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("processing", Transaction.BATCH_PAYMENTS_PROCESSING);
        params.addValue("unpaid", Transaction.BATCH_PAYMENT_UNPAID);
        return jdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) ->
                        new Candidate(
                                rs.getLong("batch_id"),
                                rs.getLong("merchant_id"),
                                rs.getLong("beneficiary_id"),
                                rs.getString("account"),
                                BigDecimal.valueOf(rs.getDouble("amount"))));
    }

    private FundingCheck fundingCheck(Candidate candidate) {
        String gatewayId = DoPayGateway.getGatewayIdByMsisdn(candidate.account(), jdbcTemplate);
        if (gatewayId == null || gatewayId.isBlank()) {
            // The payout scheduler already terminalizes unsupported beneficiaries; do not compete
            // with that path here.
            return FundingCheck.allow();
        }

        Merchant merchant =
                Common.getMerchantById(Long.toString(candidate.merchantId()), jdbcTemplate);
        if (merchant == null) {
            return FundingCheck.allow();
        }

        GatewayChargeDetails chargeDetails =
                DoPayGateway.getGatewayChargeDetailsById(
                        jdbcTemplate, gatewayId, candidate.merchantId());
        double charges =
                DoPayGateway.getCustomerOutboundCharges(
                        candidate.amount().doubleValue(), chargeDetails);
        BigDecimal required =
                MoneyAmount.of(candidate.amount().add(BigDecimal.valueOf(charges)).toPlainString())
                        .asBigDecimal();

        BigDecimal ledgerAvailable =
                ledgerService.availableMerchantBalance(candidate.merchantId(), DEFAULT_CURRENCY);

        BigDecimal legacyAvailable = null;
        ArrayList<Balance> balances =
                Common.getMerchantBalances(Long.toString(candidate.merchantId()), jdbcTemplate);
        for (Balance balance : balances) {
            if (gatewayId.equals(balance.getGateway_id())) {
                legacyAvailable = BigDecimal.valueOf(balance.getAmount());
                break;
            }
        }

        boolean legacyFunded = legacyAvailable == null || legacyAvailable.compareTo(required) >= 0;
        boolean ledgerFunded = ledgerAvailable.compareTo(required) >= 0;
        return new FundingCheck(
                legacyFunded && ledgerFunded, required, ledgerAvailable, legacyAvailable, gatewayId);
    }

    private void pause(Candidate candidate, FundingCheck check) {
        transactionTemplate.executeWithoutResult(
                status -> {
                    MapSqlParameterSource batch = new MapSqlParameterSource();
                    batch.addValue("batch_id", candidate.batchId());
                    batch.addValue("processing", Transaction.BATCH_PAYMENTS_PROCESSING);
                    batch.addValue("paused", Transaction.BATCH_PAYMENTS_PAUSED);
                    int changed =
                            jdbcTemplate.update(
                                    "UPDATE "
                                            + Common.DB_TABLE_MERCHANT_BATCH_TRANSACTION_LOG
                                            + " SET status=:paused WHERE id=:batch_id AND status=:processing",
                                    batch);
                    if (changed == 0) {
                        return;
                    }

                    String reason =
                            "Batch paused: insufficient available balance before ledger reservation. "
                                    + "Required="
                                    + check.required().toPlainString()
                                    + " "
                                    + DEFAULT_CURRENCY
                                    + ", ledgerAvailable="
                                    + check.ledgerAvailable().toPlainString()
                                    + (check.legacyAvailable() == null
                                            ? ""
                                            : ", gatewayAvailable="
                                                    + check.legacyAvailable().toPlainString())
                                    + ", gateway="
                                    + check.gatewayId()
                                    + ". Fund/reconcile and explicitly resume the batch.";
                    MapSqlParameterSource beneficiary = new MapSqlParameterSource();
                    beneficiary.addValue("beneficiary_id", candidate.beneficiaryId());
                    beneficiary.addValue("reason", reason);
                    jdbcTemplate.update(
                            "UPDATE "
                                    + Common.DB_TABLE_MERCHANT_BATCH_TRANSACTION_BENEFICIARIES
                                    + " SET reason=:reason WHERE id=:beneficiary_id",
                            beneficiary);

                    LOG.log(
                            Level.WARNING,
                            "Paused unfunded payout batch "
                                    + candidate.batchId()
                                    + " at beneficiary "
                                    + candidate.beneficiaryId()
                                    + "; required="
                                    + check.required()
                                    + ", ledgerAvailable="
                                    + check.ledgerAvailable()
                                    + ", gateway="
                                    + check.gatewayId());
                });
    }

    private record Candidate(
            long batchId, long merchantId, long beneficiaryId, String account, BigDecimal amount) {}

    private record FundingCheck(
            boolean funded,
            BigDecimal required,
            BigDecimal ledgerAvailable,
            BigDecimal legacyAvailable,
            String gatewayId) {
        static FundingCheck allow() {
            return new FundingCheck(
                    true, BigDecimal.ZERO, BigDecimal.ZERO, null, "not-applicable");
        }
    }
}
