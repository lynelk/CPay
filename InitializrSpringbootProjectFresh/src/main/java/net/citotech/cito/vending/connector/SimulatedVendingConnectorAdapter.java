package net.citotech.cito.vending.connector;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** Safe sandbox connector used until a manufacturer's actual device API is configured. */
@Component
public class SimulatedVendingConnectorAdapter implements VendingConnectorAdapter {
    @Override
    public String connectorCode() {
        return "SIMULATED";
    }

    @Override
    public VendingCommandResult execute(VendingCommand command) {
        return new VendingCommandResult(
                true,
                "SIM-VEND-" + UUID.randomUUID(),
                "ACCEPTED",
                "Simulated vending command accepted");
    }
}
