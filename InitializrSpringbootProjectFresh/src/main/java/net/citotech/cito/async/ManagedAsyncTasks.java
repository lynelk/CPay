package net.citotech.cito.async;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
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
    private static final Logger LOGGER = Logger.getLogger(ManagedAsyncTasks.class.getName());
    private static volatile ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Runs {@code task} asynchronously. Unlike a raw {@code new Thread().start()}, the pool is
     * shut down gracefully with the application and any exception the task throws is logged
     * rather than silently discarded.
     */
    public static void run(String taskName, Runnable task) {
        executor.submit(() -> {
            try {
                task.run();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Async task '" + taskName + "' failed: " + e.getMessage(), e);
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        executor.close();
    }
}
