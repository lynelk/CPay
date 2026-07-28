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
 * Covers audit N9's background-safe auto-expiry sweep for invoices: a non-destructive status
 * flip on DRAFT/SENT invoices whose due_date has passed, following the exact
 * {@code @Scheduled} + enabled-flag pattern used by {@link OperationalDataCleanupScheduler}.
 */
class InvoiceExpirySchedulerTest {

    @Test
    void expiresOverdueDraftAndSentInvoices() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(any(String.class), any(MapSqlParameterSource.class))).thenReturn(3);

        InvoiceExpiryScheduler scheduler = new InvoiceExpiryScheduler(jdbcTemplate);
        ReflectionTestUtils.setField(scheduler, "enabled", true);

        scheduler.expireOverdueInvoices();

        verify(jdbcTemplate, times(1)).update(
            contains("UPDATE invoices SET status='EXPIRED'"), any(MapSqlParameterSource.class));
        verify(jdbcTemplate).update(contains("status IN ('DRAFT','SENT')"), any(MapSqlParameterSource.class));
        verify(jdbcTemplate).update(contains("due_date < CURRENT_DATE"), any(MapSqlParameterSource.class));
    }

    @Test
    void doesNothingWhenDisabled() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        InvoiceExpiryScheduler scheduler = new InvoiceExpiryScheduler(jdbcTemplate);
        ReflectionTestUtils.setField(scheduler, "enabled", false);

        scheduler.expireOverdueInvoices();

        verify(jdbcTemplate, times(0)).update(any(String.class), any(MapSqlParameterSource.class));
    }

    @Test
    void swallowsAndLogsUnexpectedFailuresRatherThanPropagating() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(any(String.class), any(MapSqlParameterSource.class)))
            .thenThrow(new RuntimeException("db unavailable"));

        InvoiceExpiryScheduler scheduler = new InvoiceExpiryScheduler(jdbcTemplate);
        ReflectionTestUtils.setField(scheduler, "enabled", true);

        scheduler.expireOverdueInvoices();
    }
}
