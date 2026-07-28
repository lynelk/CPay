package net.citotech.cito.scheduler;

import net.citotech.cito.reconciliation.SettlementScheduleService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "cpay.settlements.sweep.enabled", havingValue = "true", matchIfMissing = true)
public class SettlementSweepScheduler {
    private final SettlementScheduleService settlementScheduleService;

    public SettlementSweepScheduler(SettlementScheduleService settlementScheduleService) {
        this.settlementScheduleService = settlementScheduleService;
    }

    @Scheduled(cron = "${cpay.settlements.sweep.cron:0 5 * * * *}")
    public void runDueSweeps() {
        settlementScheduleService.runDueSweeps();
    }
}
