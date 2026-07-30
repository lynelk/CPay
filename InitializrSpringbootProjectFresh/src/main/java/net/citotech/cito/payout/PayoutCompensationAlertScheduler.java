package net.citotech.cito.payout;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.citotech.cito.Common;
import net.citotech.cito.Model.Setting;
import net.citotech.cito.SendMail;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Alerts on payout compensation sagas (audit B3) that have sat in a non-terminal state
 * (STARTED/IN_PROGRESS/STUCK) longer than expected - meaning a failed payout's fund reversal didn't
 * finish and the ledger/statement balances may be inconsistent for that transaction.
 *
 * <p>Settings consumed: {@code payout_compensation_alert_email} (falls back to {@code
 * float_alert_email} if unset).
 */
@Component
public class PayoutCompensationAlertScheduler {
    private static final Logger logger =
            Logger.getLogger(PayoutCompensationAlertScheduler.class.getName());
    private static final int STUCK_THRESHOLD_MINUTES = 15;

    @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired private PayoutCompensationSagaService sagaService;

    @Autowired private SendMail emailService;

    @Scheduled(fixedDelay = 600000) // every 10 minutes
    @SchedulerLock(
            name = "payoutCompensationAlert",
            lockAtMostFor = "PT10M",
            lockAtLeastFor = "PT1M")
    public void checkForStuckCompensations() {
        try {
            List<PayoutCompensationSagaService.StuckSaga> stuck =
                    sagaService.findStuckOlderThan(STUCK_THRESHOLD_MINUTES);
            if (stuck.isEmpty()) {
                return;
            }
            String alertEmail = resolveAlertEmail();
            StringBuilder body = new StringBuilder();
            body.append(
                            "The following payout compensations (fund reversals for failed payouts) have not ")
                    .append("completed within ")
                    .append(STUCK_THRESHOLD_MINUTES)
                    .append(" minutes. ")
                    .append(
                            "The customer/suspense/float balances for these transactions may be left ")
                    .append("inconsistent until manually reconciled.\n\n");
            for (PayoutCompensationSagaService.StuckSaga saga : stuck) {
                body.append(
                        String.format(
                                "tx_unique_id=%s merchant_id=%d status=%s completed_steps=%d/%d last_step=%s%s%n",
                                saga.txUniqueId(),
                                saga.merchantId(),
                                saga.sagaStatus(),
                                saga.completedSteps(),
                                saga.totalSteps(),
                                saga.lastStepName(),
                                saga.lastError() == null
                                        ? ""
                                        : (" last_error=" + saga.lastError())));
            }
            logger.log(Level.SEVERE, "STUCK PAYOUT COMPENSATIONS DETECTED: " + body);
            if (alertEmail != null) {
                emailService.sendSimpleMessage(
                        alertEmail, "[CPay] Stuck payout compensation alert", body.toString());
            }
        } catch (Exception e) {
            logger.log(
                    Level.SEVERE, "PayoutCompensationAlertScheduler error: " + e.getMessage(), e);
        }
    }

    private String resolveAlertEmail() {
        Setting dedicated = Common.getSettings("payout_compensation_alert_email", jdbcTemplate);
        if (dedicated != null && !dedicated.getSetting_value().trim().isEmpty()) {
            return dedicated.getSetting_value().trim();
        }
        Setting floatAlert = Common.getSettings("float_alert_email", jdbcTemplate);
        if (floatAlert != null && !floatAlert.getSetting_value().trim().isEmpty()) {
            return floatAlert.getSetting_value().trim();
        }
        return null;
    }
}
