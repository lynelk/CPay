package net.citotech.cito.async;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Covers audit B9/G5: legacy code previously fired callback/email work on a raw
 * {@code new Thread()} - unbounded, and any exception thrown inside vanished silently. This
 * shared executor replaces that: tasks run off-thread, and a task that throws does not crash or
 * block the caller (the exception is caught and logged instead of propagating or disappearing).
 */
class ManagedAsyncTasksTest {

    @Test
    void runsTheTaskAsynchronously() throws InterruptedException {
        CountDownLatch ran = new CountDownLatch(1);

        ManagedAsyncTasks.run("test-task", ran::countDown);

        assertThat(ran.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void aTaskThrowingDoesNotPropagateToTheCaller() {
        AtomicInteger afterCount = new AtomicInteger(0);

        ManagedAsyncTasks.run("failing-task", () -> {
            throw new RuntimeException("boom");
        });
        // The submitting thread must never see the exception - if it propagated, this line
        // would not be reached (or the test would fail with an uncaught exception).
        afterCount.incrementAndGet();

        assertThat(afterCount.get()).isEqualTo(1);
    }

    @Test
    void subsequentTasksStillRunAfterAPriorTaskFailed() throws InterruptedException {
        CountDownLatch ranAfterFailure = new CountDownLatch(1);

        ManagedAsyncTasks.run("failing-task", () -> {
            throw new RuntimeException("boom");
        });
        ManagedAsyncTasks.run("recovery-task", ranAfterFailure::countDown);

        assertThat(ranAfterFailure.await(5, TimeUnit.SECONDS)).isTrue();
    }
}
