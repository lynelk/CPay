package net.citotech.cito.scheduler;

import java.time.LocalDate;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.citotech.cito.ledger.DoubleEntryLedgerService;
import net.citotech.cito.ledger.LedgerBalanceService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "cpay.ledger.automation.enabled", havingValue = "true", matchIfMissing = true)
public class LedgerOperationsScheduler {
    private static final Logger logger = Logger.getLogger(LedgerOperationsScheduler.class.getName());

    private final DoubleEntryLedgerService ledgerService;
    private final LedgerBalanceService ledgerBalanceService;

    public LedgerOperationsScheduler(DoubleEntryLedgerService ledgerService,
                                     LedgerBalanceService ledgerBalanceService) {
        this.ledgerService = ledgerService;
        this.ledgerBalanceService = ledgerBalanceService;
    }

    @Scheduled(cron = "${cpay.ledger.trial-balance.cron:0 15 0 * * *}")
    public void runDailyTrialBalances() {
        try {
            List<String> currencies = ledgerService.activeCurrencies();
            LocalDate yesterday = LocalDate.now().minusDays(1);
            for (String currency : currencies) {
                ledgerService.runTrialBalance(yesterday, currency);
            }
            ledgerBalanceService.refreshBalances();
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Ledger automation failed: " + ex.getMessage(), ex);
        }
    }
}
