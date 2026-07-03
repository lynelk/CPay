package net.citotech.cito.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Lightweight in-memory replay protection for /api/v2 signed requests.
 *
 * This is suitable for a single-node deployment. For clustered production, move
 * the nonce store to Redis or the database with a short TTL.
 */
@Service
public class ReplayProtectionService {
    private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);
    private static final Duration NONCE_TTL = Duration.ofMinutes(10);

    private final Map<String, Instant> seenNonces = new ConcurrentHashMap<>();

    public boolean accept(String merchantNumber, String timestamp, String nonce) {
        cleanupExpiredNonces();
        if (isBlank(merchantNumber) || isBlank(timestamp) || isBlank(nonce)) {
            return false;
        }
        Instant requestTime;
        try {
            requestTime = Instant.parse(timestamp.trim());
        } catch (Exception e) {
            return false;
        }
        Instant now = Instant.now();
        if (requestTime.isBefore(now.minus(MAX_CLOCK_SKEW)) || requestTime.isAfter(now.plus(MAX_CLOCK_SKEW))) {
            return false;
        }
        String key = merchantNumber.trim() + ":" + nonce.trim();
        return seenNonces.putIfAbsent(key, now.plus(NONCE_TTL)) == null;
    }

    private void cleanupExpiredNonces() {
        Instant now = Instant.now();
        seenNonces.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
