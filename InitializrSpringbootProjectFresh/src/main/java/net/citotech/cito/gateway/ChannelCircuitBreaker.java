package net.citotech.cito.gateway;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Per-channel (per gateway/provider) circuit breaker (audit B5). Health-based circuit breaking
 * was previously entirely absent - a provider outage meant every payin/payout to that channel
 * paid the full timeout cost one request at a time, with no protection against cascading
 * failures.
 *
 * <p>States, one instance per channel code:
 * <ul>
 *   <li>CLOSED - requests flow normally.
 *   <li>OPEN - opened after {@code failureThreshold} consecutive failures; requests are
 *       short-circuited (rejected immediately, no network call) until {@code openDurationMs} has
 *       elapsed.
 *   <li>HALF_OPEN - after the open duration, exactly one probe request is allowed through; success
 *       closes the breaker, failure re-opens it for another full duration.
 * </ul>
 */
@Component
public class ChannelCircuitBreaker {
    public enum State { CLOSED, OPEN, HALF_OPEN }

    @Value("${cpay.circuitbreaker.failure-threshold:5}")
    private int failureThreshold;

    @Value("${cpay.circuitbreaker.open-duration-ms:60000}")
    private long openDurationMs;

    private final Map<String, ChannelState> channels = new ConcurrentHashMap<>();

    /** Returns true if a request to this channel should proceed (CLOSED, or HALF_OPEN probe slot). */
    public boolean allowRequest(String channelCode) {
        ChannelState state = channelFor(channelCode);
        synchronized (state) {
            if (state.state == State.CLOSED) {
                return true;
            }
            if (state.state == State.OPEN) {
                if (!Instant.now().isBefore(state.openedAt.plusMillis(openDurationMs))) {
                    state.state = State.HALF_OPEN;
                    state.probeInFlight = true;
                    return true;
                }
                return false;
            }
            // HALF_OPEN: only one probe in flight at a time.
            if (state.probeInFlight) {
                return false;
            }
            state.probeInFlight = true;
            return true;
        }
    }

    public void recordSuccess(String channelCode) {
        ChannelState state = channelFor(channelCode);
        synchronized (state) {
            state.consecutiveFailures.set(0);
            state.state = State.CLOSED;
            state.probeInFlight = false;
        }
    }

    public void recordFailure(String channelCode) {
        ChannelState state = channelFor(channelCode);
        synchronized (state) {
            state.probeInFlight = false;
            if (state.state == State.HALF_OPEN) {
                open(state);
                return;
            }
            int failures = state.consecutiveFailures.incrementAndGet();
            if (failures >= failureThreshold) {
                open(state);
            }
        }
    }

    public State stateOf(String channelCode) {
        return channelFor(channelCode).state;
    }

    private void open(ChannelState state) {
        state.state = State.OPEN;
        state.openedAt = Instant.now();
        state.consecutiveFailures.set(0);
    }

    private ChannelState channelFor(String channelCode) {
        return channels.computeIfAbsent(channelCode, key -> new ChannelState());
    }

    private static final class ChannelState {
        private volatile State state = State.CLOSED;
        private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        private volatile Instant openedAt;
        private volatile boolean probeInFlight = false;
    }
}
