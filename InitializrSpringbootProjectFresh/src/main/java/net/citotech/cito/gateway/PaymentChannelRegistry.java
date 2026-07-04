package net.citotech.cito.gateway;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

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

    public Optional<PaymentChannelAdapter> findByLegacyGatewayId(String gatewayId) {
        if (gatewayId == null || gatewayId.trim().isEmpty()) {
            return Optional.empty();
        }
        return adapters.stream()
                .filter(adapter -> adapter instanceof LegacyGatewayAdapter)
                .map(adapter -> (LegacyGatewayAdapter) adapter)
                .filter(adapter -> gatewayId.equals(adapter.legacyGatewayId()))
                .map(adapter -> (PaymentChannelAdapter) adapter)
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

