package net.citotech.cito.gateway;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Service;

@Service
public class GatewayExecutionService {
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public <T> T execute(Callable<T> task) {
        try {
            return executor.submit(task).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentGatewayException("Gateway execution was interrupted");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new PaymentGatewayException("Gateway execution failed");
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.close();
    }
}
