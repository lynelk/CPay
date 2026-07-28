package net.citotech.cito.security;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "cpay.security.nonce-store", havingValue = "memory", matchIfMissing = true)
public class InMemoryNonceStore implements NonceStore {
    private final Map<String, Instant> seenNonces = new ConcurrentHashMap<>();

    @Override
    public boolean remember(String merchantNumber, String nonce, Instant expiresAt) {
        cleanupExpired();
        String key = merchantNumber.trim() + ":" + nonce.trim();
        return seenNonces.putIfAbsent(key, expiresAt) == null;
    }

    @Override
    public void cleanupExpired() {
        Instant now = Instant.now();
        seenNonces.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }
}

