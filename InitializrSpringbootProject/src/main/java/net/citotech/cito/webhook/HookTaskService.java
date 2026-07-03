package net.citotech.cito.webhook;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.citotech.cito.Common;
import net.citotech.cito.Model.HttpRequestResponse;
import org.springframework.stereotype.Service;

@Service
public class HookTaskService {
    private final HookTaskRepository repository;

    public HookTaskService(HookTaskRepository repository) {
        this.repository = repository;
    }

    public void enqueue(long merchantId, String transactionId, String reference, String callbackUrl, String body) {
        repository.enqueue(merchantId, transactionId, reference, callbackUrl, body);
    }

    public int processDue(int limit) {
        List<HookTask> due = repository.findDue(limit);
        int processed = 0;
        for (HookTask task : due) {
            deliver(task);
            processed++;
        }
        return processed;
    }

    private void deliver(HookTask task) {
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            HttpRequestResponse response = Common.doHttpRequest("POST", task.getCallbackUrl(), task.getBody(), headers);
            if (response != null && response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                repository.markDelivered(task.getId());
            } else {
                String error = response == null ? "No callback response" : response.toString();
                repository.markRetry(task.getId(), task.getAttempts(), task.getMaxAttempts(), nextRetry(task.getAttempts()), error);
            }
        } catch (Exception e) {
            repository.markRetry(task.getId(), task.getAttempts(), task.getMaxAttempts(), nextRetry(task.getAttempts()), e.getMessage());
        }
    }

    private Instant nextRetry(int attempts) {
        int nextAttempt = attempts + 1;
        if (nextAttempt <= 1) {
            return Instant.now().plus(Duration.ofMinutes(1));
        }
        if (nextAttempt == 2) {
            return Instant.now().plus(Duration.ofMinutes(5));
        }
        if (nextAttempt == 3) {
            return Instant.now().plus(Duration.ofMinutes(15));
        }
        return Instant.now().plus(Duration.ofHours(1));
    }
}
