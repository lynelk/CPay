package net.citotech.cito.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Covers audit B5: per-channel circuit breaking, previously entirely absent - a failing provider
 * would otherwise be hit one request at a time, each paying the full timeout.
 */
class ChannelCircuitBreakerTest {

    @Test
    void staysClosedAndAllowsRequestsBelowTheFailureThreshold() {
        ChannelCircuitBreaker breaker = breaker(3, 60000);

        breaker.recordFailure("mtn");
        breaker.recordFailure("mtn");

        assertThat(breaker.allowRequest("mtn")).isTrue();
        assertThat(breaker.stateOf("mtn")).isEqualTo(ChannelCircuitBreaker.State.CLOSED);
    }

    @Test
    void opensAfterConsecutiveFailuresReachTheThresholdAndShortCircuits() {
        ChannelCircuitBreaker breaker = breaker(3, 60000);

        breaker.recordFailure("mtn");
        breaker.recordFailure("mtn");
        breaker.recordFailure("mtn");

        assertThat(breaker.stateOf("mtn")).isEqualTo(ChannelCircuitBreaker.State.OPEN);
        assertThat(breaker.allowRequest("mtn")).isFalse();
    }

    @Test
    void aSuccessResetsTheFailureCountAndKeepsTheBreakerClosed() {
        ChannelCircuitBreaker breaker = breaker(3, 60000);

        breaker.recordFailure("mtn");
        breaker.recordFailure("mtn");
        breaker.recordSuccess("mtn");
        breaker.recordFailure("mtn");
        breaker.recordFailure("mtn");

        assertThat(breaker.stateOf("mtn")).isEqualTo(ChannelCircuitBreaker.State.CLOSED);
        assertThat(breaker.allowRequest("mtn")).isTrue();
    }

    @Test
    void transitionsToHalfOpenAfterTheCooldownAndAllowsOneProbe() {
        ChannelCircuitBreaker breaker = breaker(1, 0); // 0ms cooldown - immediately eligible

        breaker.recordFailure("mtn");
        assertThat(breaker.stateOf("mtn")).isEqualTo(ChannelCircuitBreaker.State.OPEN);

        // Cooldown has already elapsed (0ms), so the next allowRequest should probe.
        assertThat(breaker.allowRequest("mtn")).isTrue();
        assertThat(breaker.stateOf("mtn")).isEqualTo(ChannelCircuitBreaker.State.HALF_OPEN);
        // A second concurrent probe should not be let through while one is in flight.
        assertThat(breaker.allowRequest("mtn")).isFalse();
    }

    @Test
    void aFailedProbeReopensTheBreaker() {
        ChannelCircuitBreaker breaker = breaker(1, 0);
        breaker.recordFailure("mtn");
        breaker.allowRequest("mtn"); // moves to HALF_OPEN, consumes the probe slot

        breaker.recordFailure("mtn");

        assertThat(breaker.stateOf("mtn")).isEqualTo(ChannelCircuitBreaker.State.OPEN);
    }

    @Test
    void aSuccessfulProbeClosesTheBreaker() {
        ChannelCircuitBreaker breaker = breaker(1, 0);
        breaker.recordFailure("mtn");
        breaker.allowRequest("mtn");

        breaker.recordSuccess("mtn");

        assertThat(breaker.stateOf("mtn")).isEqualTo(ChannelCircuitBreaker.State.CLOSED);
        assertThat(breaker.allowRequest("mtn")).isTrue();
    }

    @Test
    void channelsAreTrackedIndependently() {
        ChannelCircuitBreaker breaker = breaker(1, 60000);

        breaker.recordFailure("mtn");

        assertThat(breaker.stateOf("mtn")).isEqualTo(ChannelCircuitBreaker.State.OPEN);
        assertThat(breaker.stateOf("airtel")).isEqualTo(ChannelCircuitBreaker.State.CLOSED);
        assertThat(breaker.allowRequest("airtel")).isTrue();
    }

    private ChannelCircuitBreaker breaker(int failureThreshold, long openDurationMs) {
        ChannelCircuitBreaker breaker = new ChannelCircuitBreaker();
        ReflectionTestUtils.setField(breaker, "failureThreshold", failureThreshold);
        ReflectionTestUtils.setField(breaker, "openDurationMs", openDurationMs);
        return breaker;
    }
}
