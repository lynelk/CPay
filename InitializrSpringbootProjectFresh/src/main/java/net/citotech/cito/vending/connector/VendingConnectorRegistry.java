package net.citotech.cito.vending.connector;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.stereotype.Service;

@Service
public class VendingConnectorRegistry {
    private final Map<String, VendingConnectorAdapter> adapters = new LinkedHashMap<>();

    public VendingConnectorRegistry(List<VendingConnectorAdapter> discovered) {
        for (VendingConnectorAdapter adapter : discovered) {
            adapters.put(normalize(adapter.connectorCode()), adapter);
        }
    }

    public VendingConnectorAdapter require(String connectorCode) {
        VendingConnectorAdapter adapter = adapters.get(normalize(connectorCode));
        if (adapter == null) {
            throw new PaymentGatewayException(
                    "Vending connector is not configured: " + connectorCode);
        }
        return adapter;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
