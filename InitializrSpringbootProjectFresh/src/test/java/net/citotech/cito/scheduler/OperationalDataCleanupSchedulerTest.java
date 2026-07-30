package net.citotech.cito.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Covers audit F5/F7 (operational purge coverage) and E4 (an absolute session-lifetime cap - Spring
 * Session's own expiry is inactivity-only, so a session kept active indefinitely would otherwise
 * never expire).
 */
class OperationalDataCleanupSchedulerTest {

    @Test
    void purgesOperationalDataCategories() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(any(String.class), any(MapSqlParameterSource.class)))
                .thenReturn(1);

        OperationalDataCleanupScheduler scheduler =
                new OperationalDataCleanupScheduler(jdbcTemplate);
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        ReflectionTestUtils.setField(scheduler, "apiRateLimitRetentionMinutes", 1440L);
        ReflectionTestUtils.setField(scheduler, "callbackClaimRetentionHours", 24L);
        ReflectionTestUtils.setField(scheduler, "passwordResetTokenRetentionDays", 7L);
        ReflectionTestUtils.setField(scheduler, "webhookDeliveryRetentionDays", 30L);
        ReflectionTestUtils.setField(scheduler, "callbackTaskRetentionDays", 30L);
        ReflectionTestUtils.setField(scheduler, "callbackSignatureRetentionDays", 30L);
        ReflectionTestUtils.setField(scheduler, "providerRunRetentionDays", 90L);
        ReflectionTestUtils.setField(scheduler, "sessionAbsoluteMaxHours", 12L);

        scheduler.cleanupOperationalData();

        verify(jdbcTemplate).update(contains("api_rate_limits"), any(MapSqlParameterSource.class));
        verify(jdbcTemplate)
                .update(contains("callback_task_claims"), any(MapSqlParameterSource.class));
        verify(jdbcTemplate)
                .update(contains("password_reset_tokens"), any(MapSqlParameterSource.class));
        verify(jdbcTemplate)
                .update(contains("merchant_webhook_deliveries"), any(MapSqlParameterSource.class));
        verify(jdbcTemplate)
                .update(contains("callback_delivery_signatures"), any(MapSqlParameterSource.class));
        verify(jdbcTemplate)
                .update(
                        org.mockito.ArgumentMatchers.argThat(
                                sql ->
                                        sql.startsWith(
                                                "DELETE FROM callback_tasks WHERE task_status='DONE'")),
                        any(MapSqlParameterSource.class));
        verify(jdbcTemplate)
                .update(contains("provider_endpoint_runs"), any(MapSqlParameterSource.class));
        verify(jdbcTemplate)
                .update(contains("provider_sandbox_runs"), any(MapSqlParameterSource.class));
        verify(jdbcTemplate)
                .update(
                        contains("provider_statement_validation_runs"),
                        any(MapSqlParameterSource.class));
        verify(jdbcTemplate).update(contains("SPRING_SESSION"), any(MapSqlParameterSource.class));
        verify(jdbcTemplate, times(10)).update(any(String.class), any(MapSqlParameterSource.class));
    }

    @Test
    void sessionExpiryUsesEpochMillisNotATimestamp() {
        // SPRING_SESSION.CREATION_TIME is a BIGINT of epoch millis, not a SQL TIMESTAMP column -
        // passing a java.sql.Timestamp there would never match.
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(any(String.class), any(MapSqlParameterSource.class)))
                .thenReturn(0);
        OperationalDataCleanupScheduler scheduler =
                new OperationalDataCleanupScheduler(jdbcTemplate);
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        ReflectionTestUtils.setField(scheduler, "sessionAbsoluteMaxHours", 12L);

        scheduler.cleanupOperationalData();

        verify(jdbcTemplate)
                .update(
                        contains("SPRING_SESSION"),
                        org.mockito.ArgumentMatchers.argThat(
                                (MapSqlParameterSource p) -> p.getValue("cutoff") instanceof Long));
    }

    @Test
    void doesNothingWhenDisabled() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        OperationalDataCleanupScheduler scheduler =
                new OperationalDataCleanupScheduler(jdbcTemplate);
        ReflectionTestUtils.setField(scheduler, "enabled", false);

        scheduler.cleanupOperationalData();

        verify(jdbcTemplate, times(0)).update(any(String.class), any(MapSqlParameterSource.class));
    }
}
