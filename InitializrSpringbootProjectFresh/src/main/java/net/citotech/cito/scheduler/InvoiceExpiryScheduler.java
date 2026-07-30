package net.citotech.cito.scheduler;

import java.util.logging.Level;
import java.util.logging.Logger;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Auto-expires DRAFT/SENT invoices (audit N9) whose due_date has passed, so an invoice doesn't stay
 * payable indefinitely just because nobody ever opened its pay link - {@code InvoiceService} only
 * lazily flips an overdue invoice to EXPIRED when its pay link *is* opened; this sweep covers the
 * ones that never are. Purely a status flip on an additive column, so - like {@link
 * OperationalDataCleanupScheduler} - it is non-destructive and enabled by default.
 */
@Component
public class InvoiceExpiryScheduler {
    private static final Logger logger = Logger.getLogger(InvoiceExpiryScheduler.class.getName());

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Value("${cpay.invoices.expiry.enabled:true}")
    private boolean enabled;

    public InvoiceExpiryScheduler(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(fixedDelayString = "${cpay.invoices.expiry.fixed-delay-ms:3600000}")
    @SchedulerLock(name = "invoiceExpiry", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void expireOverdueInvoices() {
        if (!enabled) {
            return;
        }
        try {
            int expired =
                    jdbcTemplate.update(
                            "UPDATE invoices SET status='EXPIRED' "
                                    + "WHERE status IN ('DRAFT','SENT') AND due_date IS NOT NULL AND due_date < CURRENT_DATE",
                            new MapSqlParameterSource());
            if (expired > 0) {
                logger.log(
                        Level.INFO,
                        "Invoice expiry sweep flipped {0} overdue invoice(s) to EXPIRED",
                        expired);
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Invoice expiry sweep failed: " + ex.getMessage(), ex);
        }
    }
}
