package net.citotech.cito.scheduler;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OperationalDataCleanupScheduler {
    private static final Logger logger = Logger.getLogger(OperationalDataCleanupScheduler.class.getName());

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Value("${cpay.cleanup.enabled:true}")
    private boolean enabled;

    @Value("${cpay.cleanup.api-rate-limit-retention-minutes:1440}")
    private long apiRateLimitRetentionMinutes;

    @Value("${cpay.cleanup.callback-claim-retention-hours:24}")
    private long callbackClaimRetentionHours;

    @Value("${cpay.cleanup.password-reset-token-retention-days:7}")
    private long passwordResetTokenRetentionDays;

    @Value("${cpay.cleanup.webhook-delivery-retention-days:30}")
    private long webhookDeliveryRetentionDays;

    @Value("${cpay.security.session-absolute-max-hours:12}")
    private long sessionAbsoluteMaxHours;

    public OperationalDataCleanupScheduler(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(fixedDelayString = "${cpay.cleanup.fixed-delay-ms:3600000}")
    public void cleanupOperationalData() {
        if (!enabled) {
            return;
        }
        try {
            int rateRows = deleteOlderThan(
                    "DELETE FROM api_rate_limits WHERE created_at < :cutoff",
                    Instant.now().minus(apiRateLimitRetentionMinutes, ChronoUnit.MINUTES));
            int staleClaims = deleteOlderThan(
                    "DELETE FROM callback_task_claims WHERE claim_status='ACTIVE' AND created_at < :cutoff",
                    Instant.now().minus(callbackClaimRetentionHours, ChronoUnit.HOURS));
            int resetTokens = deleteOlderThan(
                    "DELETE FROM password_reset_tokens WHERE created_at < :cutoff",
                    Instant.now().minus(passwordResetTokenRetentionDays, ChronoUnit.DAYS));
            int webhookDeliveries = deleteOlderThan(
                    "DELETE FROM merchant_webhook_deliveries WHERE delivery_status IN ('DELIVERED','FAILED') AND created_at < :cutoff",
                    Instant.now().minus(webhookDeliveryRetentionDays, ChronoUnit.DAYS));
            // Spring Session only expires sessions by inactivity (EXPIRY_TIME); this enforces an
            // absolute cap regardless of activity, so a session can't be kept alive indefinitely
            // by staying active (audit E4).
            int expiredSessions = expireSessionsOlderThan(
                    Instant.now().minus(sessionAbsoluteMaxHours, ChronoUnit.HOURS));
            if (rateRows > 0 || staleClaims > 0 || resetTokens > 0 || webhookDeliveries > 0 || expiredSessions > 0) {
                logger.log(Level.INFO, "Operational cleanup removed api_rate_limits={0}, stale_callback_claims={1}, "
                                + "password_reset_tokens={2}, webhook_deliveries={3}, sessions_past_absolute_max={4}",
                        new Object[]{rateRows, staleClaims, resetTokens, webhookDeliveries, expiredSessions});
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Operational cleanup failed: " + ex.getMessage(), ex);
        }
    }

    private int deleteOlderThan(String sql, Instant cutoff) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("cutoff", Timestamp.from(cutoff));
        return jdbcTemplate.update(sql, parameters);
    }

    private int expireSessionsOlderThan(Instant cutoff) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("cutoff", cutoff.toEpochMilli());
        return jdbcTemplate.update(
                "DELETE FROM SPRING_SESSION WHERE CREATION_TIME < :cutoff", parameters);
    }
}
