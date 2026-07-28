package net.citotech.cito.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Covers the F4/N10/O3 nightly reporting scheduler: it delegates to
 * {@link ReportingAggregateService} for "yesterday" when enabled, and is a complete no-op
 * (including on exceptions from the service) when disabled - mirroring
 * OperationalDataCleanupSchedulerTest's enabled/disabled gating coverage.
 */
class ReportingAggregateSchedulerTest {

    @Test
    void runsYesterdaysAggregationWhenEnabled() {
        ReportingAggregateService aggregateService = mock(ReportingAggregateService.class);
        ReportingAggregateScheduler scheduler = new ReportingAggregateScheduler(aggregateService);
        ReflectionTestUtils.setField(scheduler, "enabled", true);

        scheduler.runNightlyAggregation();

        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(aggregateService).aggregateForDate(dateCaptor.capture());
        assertThat(dateCaptor.getValue()).isEqualTo(LocalDate.now().minusDays(1));
    }

    @Test
    void doesNothingWhenDisabled() {
        ReportingAggregateService aggregateService = mock(ReportingAggregateService.class);
        ReportingAggregateScheduler scheduler = new ReportingAggregateScheduler(aggregateService);
        ReflectionTestUtils.setField(scheduler, "enabled", false);

        scheduler.runNightlyAggregation();

        verify(aggregateService, never()).aggregateForDate(any(LocalDate.class));
    }

    @Test
    void swallowsExceptionsFromTheAggregateServiceSoTheSchedulerNeverThrows() {
        ReportingAggregateService aggregateService = mock(ReportingAggregateService.class);
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(aggregateService).aggregateForDate(any(LocalDate.class));
        ReportingAggregateScheduler scheduler = new ReportingAggregateScheduler(aggregateService);
        ReflectionTestUtils.setField(scheduler, "enabled", true);

        scheduler.runNightlyAggregation();

        verify(aggregateService).aggregateForDate(any(LocalDate.class));
    }
}
