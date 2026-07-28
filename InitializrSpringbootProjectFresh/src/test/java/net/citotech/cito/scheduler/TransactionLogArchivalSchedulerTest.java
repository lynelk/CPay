package net.citotech.cito.scheduler;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Covers audit F3: the archival sweep must stay fully inert until explicitly enabled (a payments
 * ledger table must not start rewriting rows the moment this ships), and purge must never run
 * unless separately enabled on top of archival.
 */
class TransactionLogArchivalSchedulerTest {

    @Test
    void doesNothingWhenDisabled() {
        TransactionLogArchivalService archivalService = mock(TransactionLogArchivalService.class);
        TransactionLogArchivalScheduler scheduler = new TransactionLogArchivalScheduler(archivalService);
        ReflectionTestUtils.setField(scheduler, "enabled", false);

        scheduler.run();

        verify(archivalService, never()).archiveBatch(anyInt(), anyInt());
        verify(archivalService, never()).purgeBatch(anyInt(), anyInt());
    }

    @Test
    void archivesInBatchesUntilNoneRemainAndSkipsPurgeWhenNotSeparatelyEnabled() {
        TransactionLogArchivalService archivalService = mock(TransactionLogArchivalService.class);
        when(archivalService.archiveBatch(365, 500)).thenReturn(500, 200, 0);
        TransactionLogArchivalScheduler scheduler = new TransactionLogArchivalScheduler(archivalService);
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        ReflectionTestUtils.setField(scheduler, "retentionDays", 365);
        ReflectionTestUtils.setField(scheduler, "batchSize", 500);
        ReflectionTestUtils.setField(scheduler, "purgeEnabled", false);

        scheduler.run();

        verify(archivalService, times(3)).archiveBatch(365, 500);
        verify(archivalService, never()).purgeBatch(anyInt(), anyInt());
    }

    @Test
    void runsPurgeOnlyWhenSeparatelyEnabled() {
        TransactionLogArchivalService archivalService = mock(TransactionLogArchivalService.class);
        when(archivalService.archiveBatch(365, 500)).thenReturn(0);
        when(archivalService.purgeBatch(730, 500)).thenReturn(10, 0);
        TransactionLogArchivalScheduler scheduler = new TransactionLogArchivalScheduler(archivalService);
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        ReflectionTestUtils.setField(scheduler, "retentionDays", 365);
        ReflectionTestUtils.setField(scheduler, "batchSize", 500);
        ReflectionTestUtils.setField(scheduler, "purgeEnabled", true);
        ReflectionTestUtils.setField(scheduler, "purgeAfterDays", 730);

        scheduler.run();

        verify(archivalService, times(2)).purgeBatch(730, 500);
    }
}
