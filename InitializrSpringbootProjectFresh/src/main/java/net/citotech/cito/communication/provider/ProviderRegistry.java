package net.citotech.cito.communication.provider;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.citotech.cito.communication.domain.CommunicationChannel;
import org.springframework.stereotype.Component;

/**
 * Central lookup of channel-neutral provider adapters (ISO domain mapping: communication/provider).
 * Adapters are keyed by stable {@code providerCode}+channel, so routing rules reference database
 * identifiers while Java class names stay internal. Duplicate registrations are rejected at
 * startup rather than silently overriding one adapter with another.
 */
@Component
public class ProviderRegistry {

    private final Map<Key, CommunicationProviderAdapter> adapters;

    public ProviderRegistry(List<CommunicationProviderAdapter> adapters) {
        this.adapters =
                adapters.stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        adapter -> new Key(adapter.providerCode(), adapter.channel()),
                                        Function.identity()));
    }

    public Optional<CommunicationProviderAdapter> find(String providerCode, CommunicationChannel channel) {
        if (providerCode == null || channel == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(adapters.get(new Key(providerCode.trim().toUpperCase(), channel)));
    }

    public List<CommunicationProviderAdapter> all() {
        return List.copyOf(adapters.values());
    }

    private record Key(String providerCode, CommunicationChannel channel) {}
}
