package net.citotech.cito.security;

import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * Replay protection for /api/v2 signed requests.
 *
 * JDBC storage is the default so multiple app instances share replay state.
 * The in-memory store is available only with cpay.security.nonce-store=memory
 * for isolated local tests.
 */
@Service
public class ReplayProtectionService {
    private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);
    private static final Duration NONCE_TTL = Duration.ofMinutes(10);

    private final NonceStore nonceStore;

    public ReplayProtectionService(NonceStore nonceStore) {
        this.nonceStore = nonceStore;
    }

    public boolean accept(String merchantNumber, String timestamp, String nonce) {
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
        return nonceStore.remember(merchantNumber, nonce, now.plus(NONCE_TTL));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

