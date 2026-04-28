package net.citotech.cito.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginRateLimiterTest {

    private LoginRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new LoginRateLimiter();
    }

    @Test
    void allowsAttemptsUpToMaxLimit() {
        String ip = "192.168.1.1";
        for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS; i++) {
            assertTrue(rateLimiter.tryConsume(ip),
                    "Attempt " + (i + 1) + " should be allowed");
        }
    }

    @Test
    void blocksAttemptBeyondMaxLimit() {
        String ip = "10.0.0.1";
        for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS; i++) {
            rateLimiter.tryConsume(ip);
        }
        assertFalse(rateLimiter.tryConsume(ip), "Attempt beyond limit should be blocked");
    }

    @Test
    void isolatesCounterPerIdentifier() {
        String ip1 = "1.1.1.1";
        String ip2 = "2.2.2.2";

        for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS; i++) {
            rateLimiter.tryConsume(ip1);
        }
        // ip1 is now exhausted; ip2 should still be allowed
        assertTrue(rateLimiter.tryConsume(ip2), "Different IP should not be affected");
    }

    @Test
    void recordSuccessResetsCounter() {
        String ip = "172.16.0.1";
        for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS; i++) {
            rateLimiter.tryConsume(ip);
        }
        assertFalse(rateLimiter.tryConsume(ip), "Should be blocked before reset");

        rateLimiter.recordSuccess(ip);
        assertTrue(rateLimiter.tryConsume(ip), "Should be allowed again after success reset");
    }

    @Test
    void firstAttemptAlwaysAllowed() {
        assertTrue(rateLimiter.tryConsume("brand-new-ip"), "First attempt must always be allowed");
    }

    @Test
    void counterDoesNotExceedMaxPlusOne() {
        String ip = "99.99.99.99";
        for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS + 10; i++) {
            rateLimiter.tryConsume(ip);
        }
        // After many attempts, system stays in blocked state without overflow
        assertFalse(rateLimiter.tryConsume(ip), "Should remain blocked after many attempts");
    }

    @Test
    void windowResetAllowsNewAttempts() throws Exception {
        // We cannot easily manipulate real time in a unit test, so we verify the
        // window-reset branch by reflection to fast-forward the window start.
        String ip = "88.88.88.88";
        for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS; i++) {
            rateLimiter.tryConsume(ip);
        }
        assertFalse(rateLimiter.tryConsume(ip), "Should be blocked");

        // Use reflection to rewind the window start past WINDOW_MS
        var stateField = LoginRateLimiter.class.getDeclaredField("state");
        stateField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.ConcurrentHashMap<String, Object> state =
                (java.util.concurrent.ConcurrentHashMap<String, Object>) stateField.get(rateLimiter);

        Class<?> entryClass = Class.forName("net.citotech.cito.security.LoginRateLimiter$Entry");
        var countField = entryClass.getDeclaredField("count");
        var windowStartField = entryClass.getDeclaredField("windowStart");
        countField.setAccessible(true);
        windowStartField.setAccessible(true);

        Object entry = state.get(ip);
        // Rewind windowStart so the window appears expired
        long expiredStart = System.currentTimeMillis() - LoginRateLimiter.WINDOW_MS - 1_000L;
        var ctor = entryClass.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object rewoundEntry = ctor.newInstance((int) countField.get(entry), expiredStart);
        state.put(ip, rewoundEntry);

        assertTrue(rateLimiter.tryConsume(ip), "Should be allowed in a fresh window");
    }
}
