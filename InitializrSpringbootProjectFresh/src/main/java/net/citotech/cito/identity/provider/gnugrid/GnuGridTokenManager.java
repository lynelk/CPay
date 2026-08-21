package net.citotech.cito.identity.provider.gnugrid;

import java.time.Instant;
import java.util.UUID;
import net.citotech.cito.gateway.ProviderToken;
import net.citotech.cito.gateway.ProviderTokenStoreService;
import net.citotech.cito.identity.provider.ValidationProviderException;
import org.springframework.stereotype.Service;

/**
 * gnuGrid CRB access-token lifecycle (ISO domain mapping: identity/provider/gnugrid). Reuses the
 * existing encrypted {@link ProviderTokenStoreService}: tokens are cached by
 * {@code (GNUGRID_CRB, OAUTH, environment)}, refreshed under a multi-instance-safe lease, and
 * stored with an expiry-skew safety margin. The manager never logs tokens.
 */
@Service
public class GnuGridTokenManager {

    public static final String PROVIDER_CODE = "GNUGRID_CRB";
    public static final String SEGMENT = "OAUTH";

    private static final long LEASE_SECONDS = 30L;
    private static final long WAIT_MAX_MILLIS = 1500L;
    private static final long WAIT_STEP_MILLIS = 100L;

    private final ProviderTokenStoreService tokenStore;
    private final GnuGridOauthClient oauthClient;
    private final GnuGridProperties properties;

    public GnuGridTokenManager(
            ProviderTokenStoreService tokenStore,
            GnuGridOauthClient oauthClient,
            GnuGridProperties properties) {
        this.tokenStore = tokenStore;
        this.oauthClient = oauthClient;
        this.properties = properties;
    }

    /** Returns a valid access token, refreshing under a lease when near expiry. */
    public String accessToken() {
        String environment = properties.environment();
        return tokenStore
                .findValid(PROVIDER_CODE, SEGMENT, environment)
                .filter(this::unexpired)
                .map(ProviderToken::getTokenValue)
                .orElseGet(() -> refresh(environment));
    }

    private String refresh(String environment) {
        String owner = UUID.randomUUID().toString();
        Instant leaseUntil = Instant.now().plusSeconds(LEASE_SECONDS);
        if (!tokenStore.acquireRefreshLease(
                PROVIDER_CODE, SEGMENT, environment, owner, leaseUntil)) {
            return waitForPeerRefresh(environment);
        }
        try {
            GnuGridOauthClient.GnuGridToken token = oauthClient.token();
            Instant expiresAt =
                    Instant.now()
                            .plusSeconds(token.expiresInSeconds())
                            .minusSeconds(properties.oauthExpirySkewSeconds());
            tokenStore.save(
                    PROVIDER_CODE,
                    SEGMENT,
                    environment,
                    token.accessToken(),
                    expiresAt);
            return token.accessToken();
        } catch (RuntimeException e) {
            throw new ValidationProviderException(
                    PROVIDER_CODE,
                    "PROVIDER_AUTHENTICATION_ERROR",
                    "gnuGrid OAuth token refresh failed");
        }
    }

    private String waitForPeerRefresh(String environment) {
        long waited = 0L;
        while (waited < WAIT_MAX_MILLIS) {
            try {
                Thread.sleep(WAIT_STEP_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            waited += WAIT_STEP_MILLIS;
            var token = tokenStore.findValid(PROVIDER_CODE, SEGMENT, environment);
            if (token.isPresent()) {
                return token.get().getTokenValue();
            }
        }
        throw new ValidationProviderException(
                PROVIDER_CODE,
                "PROVIDER_TEMPORARILY_UNAVAILABLE",
                "gnuGrid OAuth refresh is in progress elsewhere");
    }

    private boolean unexpired(ProviderToken token) {
        return token.getExpiresAt() == null || token.getExpiresAt().isAfter(Instant.now());
    }
}
