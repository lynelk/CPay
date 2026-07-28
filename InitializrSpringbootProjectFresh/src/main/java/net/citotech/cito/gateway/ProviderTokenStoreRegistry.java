package net.citotech.cito.gateway;

import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ProviderTokenStoreRegistry {
    private static ProviderTokenStoreService tokenStoreService;

    public ProviderTokenStoreRegistry(ProviderTokenStoreService tokenStoreService) {
        ProviderTokenStoreRegistry.tokenStoreService = tokenStoreService;
    }

    public static Optional<ProviderToken> findValid(String providerCode, String segment, String environment) {
        if (tokenStoreService == null) {
            return Optional.empty();
        }
        return tokenStoreService.findValid(providerCode, segment, environment);
    }

    public static void save(String providerCode, String segment, String environment, String tokenValue, Instant expiresAt) {
        if (tokenStoreService != null) {
            tokenStoreService.save(providerCode, segment, environment, tokenValue, expiresAt);
        }
    }
}
