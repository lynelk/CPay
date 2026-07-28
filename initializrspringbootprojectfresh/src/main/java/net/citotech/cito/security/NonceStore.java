package net.citotech.cito.security;

import java.time.Instant;

public interface NonceStore {
    boolean remember(String merchantNumber, String nonce, Instant expiresAt);
    void cleanupExpired();
}

