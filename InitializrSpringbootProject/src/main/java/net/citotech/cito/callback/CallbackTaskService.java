package net.citotech.cito.callback;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.citotech.cito.Common;
import net.citotech.cito.Model.HttpRequestResponse;
import org.springframework.stereotype.Service;

@Service
public class CallbackTaskService {
    private final CallbackTaskRepository repository;
    private final CallbackSigningService signingService;

    public CallbackTaskService(CallbackTaskRepository repository, CallbackSigningService signingService) {
        this.repository = repository;
        this.signingService = signingService;
    }

    public void enqueue(long merchantId, String transactionId, String referenceValue, String targetUrl, String requestBody) {
        repository.enqueue(merchantId, transactionId, referenceValue, targetUrl, requestBody);
    }

    public int processDue(int limit) {
        List<CallbackTask> due = repository.findDue(limit);
        int count = 0;
        for (CallbackTask task : due) {
            deliver(task);
            count++;
        }
        return count;
    }

    private void deliver(CallbackTask task) {
        try {
            CallbackSigningService.SignedCallback signed = signingService.sign(task);
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("X-CPay-Signature", signed.signature);
            headers.put("X-CPay-Nonce", signed.nonce);
            headers.put("X-CPay-Timestamp", signed.timestamp);
            HttpRequestResponse response = Common.doHttpRequest("POST", task.targetUrl, task.requestBody, headers);
            if (response != null && response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                repository.markDone(task.id);
            } else {
                repository.markNext(task.id, task.attemptCount, task.attemptLimit, nextRun(task.attemptCount), response == null ? "No response" : response.toString());
            }
        } catch (Exception e) {
            repository.markNext(task.id, task.attemptCount, task.attemptLimit, nextRun(task.attemptCount), e.getMessage());
        }
    }

    private Instant nextRun(int attempts) {
        int next = attempts + 1;
        if (next <= 1) return Instant.now().plus(Duration.ofMinutes(1));
        if (next == 2) return Instant.now().plus(Duration.ofMinutes(5));
        if (next == 3) return Instant.now().plus(Duration.ofMinutes(15));
        return Instant.now().plus(Duration.ofHours(1));
    }
}
