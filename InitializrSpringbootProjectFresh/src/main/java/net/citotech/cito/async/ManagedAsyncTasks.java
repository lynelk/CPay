package net.citotech.cito.async;

import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Shared fire-and-forget async executor for legacy code paths (merchant callback delivery,
 * notification emails) that previously spawned a raw, unmanaged {@code new Thread()} per call:
 * unbounded, and any exception thrown inside the thread body vanished silently instead of being
 * logged (audit B9/G5). Exposed as static methods so legacy static-utility classes ({@code
 * Common}, {@code TxCallback}) that are not themselves Spring beans can use it too.
 */
@Component
public class ManagedAsyncTasks {
    // Audit H1: converted from java.util.logging to SLF4J.
    private static final Logger logger = LoggerFactory.getLogger(ManagedAsyncTasks.class);
    private static volatile ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Runs {@code task} asynchronously. Unlike a raw {@code new Thread().start()}, the pool is
     * shut down gracefully with the application and any exception the task throws is logged
     * rather than silently discarded.
     *
     * <p>Audit H2: the calling thread's MDC context (request_id, etc, see
     * net.citotech.cito.config.RequestCorrelationFilter) is copied onto the worker thread before
     * {@code task} runs. MDC is thread-local, so without this every log line emitted from inside
     * an async task (email sending, webhook delivery, ...) would have no correlation id at all,
     * making it impossible to tie the async work back to the request that triggered it.
     */
    public static void run(String taskName, Runnable task) {
        Map<String, String> callerContext = MDC.getCopyOfContextMap();
        executor.submit(() -> {
            if (callerContext != null) {
                MDC.setContextMap(callerContext);
            }
            try {
                task.run();
            } catch (Exception e) {
                logger.error("Async task '" + taskName + "' failed: " + e.getMessage(), e);
            } finally {
                MDC.clear();
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        executor.close();
    }
}
