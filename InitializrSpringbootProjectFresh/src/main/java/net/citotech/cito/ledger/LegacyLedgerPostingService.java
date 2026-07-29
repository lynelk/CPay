package net.citotech.cito.ledger;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.Model.Transaction;
import net.citotech.cito.money.MoneyAmount;
import org.springframework.stereotype.Service;

/**
 * Posts the same double-entry ledger accounting PaymentOrchestrationService already posts for the
 * v2 orchestration path (audit A1/B1), for the legacy call sites that invoke
 * {@code Common.doPayIn}/{@code doPayOut} directly instead of going through v2: Api.java's
 * /doMobileMoneyPayIn|Out and TransactionsLogController's portal-initiated payin and batch payout.
 * Those never wrote to the double-entry ledger at all - ledger-derived balances/reporting were
 * blind to every transaction submitted through the legacy surface. {@code Common.java} itself is
 * intentionally left untouched (still zero ledger imports) so this cannot double-post for the v2
 * path, which already calls {@code DoubleEntryLedgerService} itself around the same
 * {@code Common.doPayIn}/{@code doPayOut} call - each caller posts its own entries once.
 */
@Service
public class LegacyLedgerPostingService {
    private final DoubleEntryLedgerService ledgerService;

    public LegacyLedgerPostingService(DoubleEntryLedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    /**
     * @param direction "PAYIN" or "PAYOUT" (matches Transaction.TX_TYPE_PAYIN/TX_TYPE_PAYOUT).
     */
    public void postPaymentEntries(String direction, String gatewayId, Merchant merchant, Transaction tx, Double amount, Double charges) {
        if (merchant == null || tx == null || amount == null) {
            return;
        }
        String currency = tx.getCurrency() == null || tx.getCurrency().isEmpty() ? "UGX" : tx.getCurrency().trim().toUpperCase();
        BigDecimal txAmount = MoneyAmount.of(String.valueOf(amount)).asBigDecimal();
        BigDecimal feeAmount = charges == null || charges <= 0 ? BigDecimal.ZERO : MoneyAmount.of(String.valueOf(charges)).asBigDecimal();
        String providerAccount = "provider:" + gatewayId + ":" + currency + ":float";
        boolean isPayout = Transaction.TX_TYPE_PAYOUT.equals(direction);
        String merchantAccount = "merchant:" + merchant.getId() + ":" + currency + ":" + (isPayout ? "payouts_payable" : "collections_payable");
        String feeExpenseAccount = "merchant:" + merchant.getId() + ":" + currency + ":fees";
        String feeRevenueAccount = "cpay:" + currency + ":fee_revenue";

        List<LedgerEntryCommand> entries = new ArrayList<>();
        if (isPayout) {
            entries.add(entry(merchantAccount, "Merchant payout payable", "MERCHANT_LIABILITY", "MERCHANT", merchant.getId(), "DR", txAmount, currency, tx.getTx_merchant_ref()));
            entries.add(entry(providerAccount, "Provider float", "PROVIDER_FLOAT", "PROVIDER", null, "CR", txAmount, currency, tx.getTx_merchant_ref()));
            if (feeAmount.compareTo(BigDecimal.ZERO) > 0) {
                entries.add(entry(feeExpenseAccount, "Merchant transaction fees", "MERCHANT_EXPENSE", "MERCHANT", merchant.getId(), "DR", feeAmount, currency, tx.getTx_merchant_ref()));
                entries.add(entry(feeRevenueAccount, "CPay fee revenue", "REVENUE", "SYSTEM", null, "CR", feeAmount, currency, tx.getTx_merchant_ref()));
            }
        } else {
            entries.add(entry(providerAccount, "Provider float", "PROVIDER_FLOAT", "PROVIDER", null, "DR", txAmount, currency, tx.getTx_merchant_ref()));
            entries.add(entry(merchantAccount, "Merchant collection payable", "MERCHANT_LIABILITY", "MERCHANT", merchant.getId(), "CR", txAmount, currency, tx.getTx_merchant_ref()));
        }

        ledgerService.post(
            "payment:" + tx.getTx_unique_id(),
            "PAYMENT",
            tx.getTx_unique_id(),
            direction + " " + tx.getTx_merchant_ref(),
            entries);
    }

    private LedgerEntryCommand entry(String accountCode, String accountName, String accountType, String ownerType,
            Long ownerId, String txDirection, BigDecimal amount, String currency, String memo) {
        return new LedgerEntryCommand(accountCode, accountName, accountType, ownerType, ownerId, txDirection, amount, currency, memo);
    }
}
