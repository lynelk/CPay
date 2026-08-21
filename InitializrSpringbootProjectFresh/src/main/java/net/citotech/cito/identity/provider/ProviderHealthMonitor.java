package net.citotech.cito.identity.provider;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.citotech.cito.identity.domain.CheckOutcome;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * In-memory provider health + circuit state (Track B Phase 8; ISO domain mapping:
 * identity/provider). Keeps a rolling consecutive-failure counter per provider; after
 * {@code failureThreshold} consecutive {@link CheckOutcome#ERROR} outcomes the provider is
 * considered {@code OPEN} until {@code openSeconds} elapse. This mirrors the guide's "first
 * implementation can use a consecutive failure threshold + temporary circuit_open_until" — no new
 * dependency, and per-instance state is acceptable for circuit protection because the router also
 * hard-filters on capability before consulting health. Successful outcomes (PASS/FAIL/PENDING)
 * reset the counter. {@code FAIL} is an authoritative business outcome, not a technical error, so
 * it never opens a circuit.
 */
@Service
public class ProviderHealthMonitor {

    private final int failureThreshold;
    private final Duration openDuration;

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    public ProviderHealthMonitor(
            @Value("${cpay.validation.router.circuit-failure-threshold:5}") int failureThreshold,
            @Value("${cpay.validation.router.circuit-open-seconds:60}") long openSeconds) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openDuration = Duration.ofSeconds(Math.max(1, openSeconds));
    }

    /** Records an execution outcome and returns whether the provider is currently open. */
    public boolean record(String providerCode, CheckOutcome outcome) {
        Counter counter =
                counters.computeIfAbsent(
                        providerCode,
                        code -> new Counter(0, null));
        if (outcome == CheckOutcome.ERROR) {
            counter.consecutiveFailures++;
            if (counter.consecutiveFailures >= failureThreshold) {
                counter.openUntil = Instant.now().plus(openDuration);
            }
        } else {
            counter.consecutiveFailures = 0;
            counter.openUntil = null;
        }
        return isOpen(providerCode);
    }

    public boolean isOpen(String providerCode) {
        Counter counter = counters.get(providerCode);
        if (counter == null || counter.openUntil == null) {
            return false;
        }
        if (counter.openUntil.isBefore(Instant.now())) {
            counter.openUntil = null;
            counter.consecutiveFailures = 0;
            return false;
        }
        return true;
    }

    public Optional<String> openUntil(String providerCode) {
        return isOpen(providerCode)
                ? Optional.of(counters.get(providerCode).openUntil.toString())
                : Optional.empty();
    }

    private static final class Counter {
        private int consecutiveFailures;
        private Instant openUntil;

        private Counter(int consecutiveFailures, Instant openUntil) {
            this.consecutiveFailures = consecutiveFailures;
            this.openUntil = openUntil;
        }
    }
}
