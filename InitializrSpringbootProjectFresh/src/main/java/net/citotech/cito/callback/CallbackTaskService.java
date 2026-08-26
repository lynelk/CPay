package net.citotech.cito.callback;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.citotech.cito.Common;
import net.citotech.cito.Model.HttpRequestResponse;
import net.citotech.cito.metrics.GatewayMetrics;
import org.springframework.stereotype.Service;

@Service
public class CallbackTaskService {
    private final CallbackTaskRepository repository;
    private final CallbackClaimRepository claimRepository;
    private final CallbackSigningService signingService;
    private final GatewayMetrics metrics;

    public CallbackTaskService(
            CallbackTaskRepository repository,
            CallbackClaimRepository claimRepository,
            CallbackSigningService signingService,
            GatewayMetrics metrics) {
        this.repository = repository;
        this.claimRepository = claimRepository;
        this.signingService = signingService;
        this.metrics = metrics;
    }

    public void enqueue(
            long merchantId,
            String transactionId,
            String referenceValue,
            String targetUrl,
            String requestBody) {
        repository.enqueue(merchantId, transactionId, referenceValue, targetUrl, requestBody);
    }

    public int processDue(int limit) {
        List<Long> ids = claimRepository.claimDueIds(limit);
        int count = 0;
        for (Long id : ids) {
            CallbackTask task = repository.findById(id);
            if (task != null) {
                deliver(task);
                count++;
            }
            claimRepository.release(id);
        }
        return count;
    }

    private void deliver(CallbackTask task) {
        try {
            CallbackSigningService.SignedCallback signed = signingService.sign(task);
            Map<String, String> headers = callbackHeaders(task, signed);
            HttpRequestResponse response =
                    Common.doHttpRequest("POST", task.targetUrl, task.requestBody, headers);
            if (response != null
                    && response.getStatusCode() >= 200
                    && response.getStatusCode() < 300) {
                metrics.incrementCallbackDelivery("DONE");
                repository.markDone(task.id);
            } else {
                metrics.incrementCallbackDelivery(nextStatus(task.attemptCount, task.attemptLimit));
                repository.markNext(
                        task.id,
                        task.attemptCount,
                        task.attemptLimit,
                        nextRun(task.attemptCount),
                        response == null ? "No response" : response.toString());
            }
        } catch (Exception e) {
            metrics.incrementCallbackDelivery(nextStatus(task.attemptCount, task.attemptLimit));
            repository.markNext(
                    task.id,
                    task.attemptCount,
                    task.attemptLimit,
                    nextRun(task.attemptCount),
                    e.getMessage());
        }
    }

    Map<String, String> callbackHeaders(
            CallbackTask task, CallbackSigningService.SignedCallback signed) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-CPay-Signature", signed.signature);
        headers.put("X-CPay-Signature-Version", "callback-v1");
        headers.put("X-CPay-Nonce", signed.nonce);
        headers.put("X-CPay-Timestamp", signed.timestamp);
        // These values are part of the callback signature canonical string. They must travel
        // with the request so a merchant can independently verify the signature.
        headers.put("X-CPay-Callback-Task-Id", String.valueOf(task.id));
        headers.put("X-CPay-Merchant-Id", String.valueOf(task.merchantId));
        headers.put("X-CPay-Reference", task.referenceValue == null ? "" : task.referenceValue);
        return headers;
    }

    private String nextStatus(int attempts, int attemptLimit) {
        return attempts + 1 >= attemptLimit ? "PARKED" : "RETRY";
    }

    private Instant nextRun(int attempts) {
        int next = attempts + 1;
        if (next <= 1) return Instant.now().plus(Duration.ofMinutes(1));
        if (next == 2) return Instant.now().plus(Duration.ofMinutes(5));
        if (next == 3) return Instant.now().plus(Duration.ofMinutes(15));
        return Instant.now().plus(Duration.ofHours(1));
    }
}
