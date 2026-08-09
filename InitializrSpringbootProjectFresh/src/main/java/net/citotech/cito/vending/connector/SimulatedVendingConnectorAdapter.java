package net.citotech.cito.vending.connector;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Safe sandbox connector. Its response explicitly confirms completion because no callback exists.
 */
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
                "COMPLETED",
                "Simulated vending command completed");
    }
}
