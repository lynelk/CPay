package net.citotech.cito.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.Model.Transaction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Covers audit A1/B1: legacy call sites that invoke Common.doPayIn/doPayOut directly (not through
 * v2 orchestration) previously never posted anything to the double-entry ledger. This verifies the
 * shared poster builds a balanced entry set for both directions, includes fee entries only when a
 * fee is actually charged, and posts under an idempotent, tx_unique_id-keyed reference.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class LegacyLedgerPostingServiceTest {

    @Test
    void postsABalancedEntrySetForACollection() {
        DoubleEntryLedgerService ledgerService = mock(DoubleEntryLedgerService.class);
        LegacyLedgerPostingService service = new LegacyLedgerPostingService(ledgerService);
        Merchant merchant = merchant(42L);
        Transaction tx = transaction("ref-1", "uid-1", "UGX");

        service.postPaymentEntries(Transaction.TX_TYPE_PAYIN, "MTNMoMoPaymentGateway", merchant, tx, 1000.0, 0.0);

        ArgumentCaptor<List<LedgerEntryCommand>> entriesCaptor = ArgumentCaptor.forClass(List.class);
        verify(ledgerService).post(anyString(), anyString(), anyString(), anyString(), entriesCaptor.capture());
        List<LedgerEntryCommand> entries = entriesCaptor.getValue();
        assertThat(entries).hasSize(2);
        assertBalanced(entries);
    }

    @Test
    void postsFeeEntriesOnlyWhenAFeeIsActuallyCharged() {
        DoubleEntryLedgerService ledgerService = mock(DoubleEntryLedgerService.class);
        LegacyLedgerPostingService service = new LegacyLedgerPostingService(ledgerService);
        Merchant merchant = merchant(42L);
        Transaction tx = transaction("ref-2", "uid-2", "UGX");

        service.postPaymentEntries(Transaction.TX_TYPE_PAYOUT, "AirtelMoneyPaymentGateway", merchant, tx, 5000.0, 100.0);

        ArgumentCaptor<List<LedgerEntryCommand>> entriesCaptor = ArgumentCaptor.forClass(List.class);
        verify(ledgerService).post(anyString(), anyString(), anyString(), anyString(), entriesCaptor.capture());
        List<LedgerEntryCommand> entries = entriesCaptor.getValue();
        assertThat(entries).hasSize(4);
        assertBalanced(entries);
    }

    @Test
    void postsUnderAReferenceKeyedByTheTransactionsUniqueId() {
        DoubleEntryLedgerService ledgerService = mock(DoubleEntryLedgerService.class);
        LegacyLedgerPostingService service = new LegacyLedgerPostingService(ledgerService);
        Merchant merchant = merchant(42L);
        Transaction tx = transaction("ref-3", "the-unique-id", "UGX");

        service.postPaymentEntries(Transaction.TX_TYPE_PAYIN, "MTNMoMoPaymentGateway", merchant, tx, 1000.0, 0.0);

        verify(ledgerService).post(
            org.mockito.ArgumentMatchers.eq("payment:the-unique-id"),
            org.mockito.ArgumentMatchers.eq("PAYMENT"),
            org.mockito.ArgumentMatchers.eq("the-unique-id"),
            anyString(),
            anyList());
    }

    @Test
    void doesNothingWhenMerchantOrTransactionIsMissing() {
        DoubleEntryLedgerService ledgerService = mock(DoubleEntryLedgerService.class);
        LegacyLedgerPostingService service = new LegacyLedgerPostingService(ledgerService);

        service.postPaymentEntries(Transaction.TX_TYPE_PAYIN, "MTNMoMoPaymentGateway", null, transaction("r", "u", "UGX"), 1000.0, 0.0);
        service.postPaymentEntries(Transaction.TX_TYPE_PAYIN, "MTNMoMoPaymentGateway", merchant(1L), null, 1000.0, 0.0);

        verify(ledgerService, never()).post(anyString(), anyString(), anyString(), anyString(), anyList());
    }

    private void assertBalanced(List<LedgerEntryCommand> entries) {
        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (LedgerEntryCommand entry : entries) {
            if ("DR".equals(entry.direction())) {
                debits = debits.add(entry.amount());
            } else {
                credits = credits.add(entry.amount());
            }
        }
        assertThat(debits).isEqualByComparingTo(credits);
    }

    private Merchant merchant(long id) {
        Merchant merchant = new Merchant();
        merchant.setId(id);
        return merchant;
    }

    private Transaction transaction(String merchantRef, String uniqueId, String currency) {
        Transaction tx = new Transaction();
        tx.setTx_merchant_ref(merchantRef);
        tx.setTx_unique_id(uniqueId);
        tx.setCurrency(currency);
        return tx;
    }
}
