package net.citotech.cito.security;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Simple in-memory rate-limiter for login endpoints.
 *
 * <p>Allows at most {@link #MAX_ATTEMPTS} attempts per identifier (typically an
 * IP address) within a rolling {@link #WINDOW_MS} window.  On a successful
 * authentication the caller should invoke {@link #recordSuccess(String)} to
 * reset the counter.
 *
 * <p>Note: this implementation is adequate for single-instance deployments. For
 * horizontally-scaled environments, replace with a distributed store such as
 * Redis.
 */
@Component
public class LoginRateLimiter {

    /** Maximum login attempts allowed inside a single time window. */
    static final int MAX_ATTEMPTS = 5;

    /** Length of the sliding time window in milliseconds (15 minutes). */
    static final long WINDOW_MS = 15 * 60 * 1_000L;

    private final ConcurrentHashMap<String, Entry> state = new ConcurrentHashMap<>();

    /**
     * Returns {@code true} if the request is within the allowed rate, and
     * records the attempt.  Returns {@code false} when the limit is exceeded.
     *
     * @param identifier typically the client's IP address
     */
    public boolean tryConsume(String identifier) {
        long now = System.currentTimeMillis();
        Entry entry = state.compute(identifier, (key, existing) -> {
            if (existing == null || now - existing.windowStart > WINDOW_MS) {
                // New window
                return new Entry(1, now);
            }
            return new Entry(existing.count + 1, existing.windowStart);
        });
        return entry.count <= MAX_ATTEMPTS;
    }

    /**
     * Resets the counter for the given identifier after a successful login.
     *
     * @param identifier typically the client's IP address
     */
    public void recordSuccess(String identifier) {
        state.remove(identifier);
    }

    // -------------------------------------------------------------------------

    private static final class Entry {
        final int count;
        final long windowStart;

        Entry(int count, long windowStart) {
            this.count = count;
            this.windowStart = windowStart;
        }
    }
}
