package net.citotech.cito.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * P0 section 6: static failure counter for merchant API signature verification.
 *
 * <p>{@code SignatureVerificationService} is a 100% static utility called from legacy controllers,
 * so it cannot participate in Spring dependency injection without reworking every call site. This
 * registry is the minimal static seam: the service records a tagged reason on each distinct
 * rejection path (keeping its behavior byte-identical), and the Spring-managed {@code
 * ObservabilityScorecardService} exposes the totals as Micrometer gauges.
 *
 * <p>The counters are monotonic per reason (never reset) so a restart is the only way a spike is
 * forgotten - exactly what a signing-failure rate alert needs.
 */
public final class SignatureVerificationFailureRegistry {

    private static final Map<String, AtomicLong> BY_REASON = new ConcurrentHashMap<>();

    private SignatureVerificationFailureRegistry() {
        // Static utility - no instances.
    }

    /** Records one failure for the given reason code (e.g. "115", "116", "122"). */
    public static void record(String reason) {
        if (reason == null) {
            return;
        }
        BY_REASON.computeIfAbsent(reason, ignored -> new AtomicLong()).incrementAndGet();
    }

    /** Returns the total number of recorded failures across all reasons. */
    public static long total() {
        return BY_REASON.values().stream().mapToLong(AtomicLong::get).sum();
    }

    /** Returns a snapshot copy of reason -> count. */
    public static Map<String, Long> snapshot() {
        Map<String, Long> snapshot = new ConcurrentHashMap<>();
        BY_REASON.forEach((reason, counter) -> snapshot.put(reason, counter.get()));
        return snapshot;
    }
}
