package net.citotech.cito.gateway;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Registry for payment channel adapters.
 *
 * This gives the codebase a Spring-managed extension point for adding channels
 * without modifying the legacy DoPayGateway routing class every time a provider
 * is added. Existing integrations can be moved behind adapters incrementally.
 */
@Component
public class PaymentChannelRegistry {
    private final List<PaymentChannelAdapter> adapters;

    public PaymentChannelRegistry(List<PaymentChannelAdapter> adapters) {
        this.adapters = adapters;
    }

    public Collection<PaymentChannelAdapter> getAdapters() {
        return adapters;
    }

    public Optional<PaymentChannelAdapter> findByChannelCode(String channelCode) {
        if (channelCode == null || channelCode.trim().isEmpty()) {
            return Optional.empty();
        }
        return adapters.stream()
                .filter(adapter -> adapter.channelCode().equalsIgnoreCase(channelCode.trim()))
                .findFirst();
    }

    public Optional<PaymentChannelAdapter> findByAccountIdentifier(String accountIdentifier) {
        if (accountIdentifier == null || accountIdentifier.trim().isEmpty()) {
            return Optional.empty();
        }
        return adapters.stream()
                .filter(adapter -> adapter.supportsAccount(accountIdentifier.trim()))
                .sorted(Comparator.comparing(PaymentChannelAdapter::channelCode))
                .findFirst();
    }

    public List<PaymentChannelAdapter> listByCountryAndCurrency(String countryCode, String currencyCode) {
        return adapters.stream()
                .filter(adapter -> equalsIgnoreCase(adapter.countryCode(), countryCode))
                .filter(adapter -> equalsIgnoreCase(adapter.currencyCode(), currencyCode))
                .collect(Collectors.toList());
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }
}
