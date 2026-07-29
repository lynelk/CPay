package net.citotech.cito.gateway;

import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Static access point so legacy provider gateway classes (instantiated directly with {@code new},
 * not as Spring beans - e.g. {@code SafariComPaymentGateway}) can reach the DB-backed
 * {@link ProviderConversationReferenceStoreService} singleton. Mirrors the
 * {@code ProviderTokenStoreRegistry} pattern used for provider OAuth tokens.
 */
@Component
public class ProviderConversationReferenceStoreRegistry {
    private static ProviderConversationReferenceStoreService store;

    public ProviderConversationReferenceStoreRegistry(ProviderConversationReferenceStoreService store) {
        ProviderConversationReferenceStoreRegistry.store = store;
    }

    public static void save(String providerCode, String conversationId, String txReference) {
        if (store != null) {
            store.save(providerCode, conversationId, txReference);
        }
    }

    public static Optional<String> find(String providerCode, String conversationId) {
        if (store == null) {
            return Optional.empty();
        }
        return store.find(providerCode, conversationId);
    }

    public static void delete(String providerCode, String conversationId) {
        if (store != null) {
            store.delete(providerCode, conversationId);
        }
    }
}
