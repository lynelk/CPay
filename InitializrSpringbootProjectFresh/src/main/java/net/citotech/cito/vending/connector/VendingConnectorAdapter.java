package net.citotech.cito.vending.connector;

import java.util.Map;

/**
 * Manufacturer/device integration seam. A ChargeNow cabinet or another vending platform is added
 * by implementing this contract instead of contaminating rental/payment logic with vendor-specific
 * HTTP calls.
 */
public interface VendingConnectorAdapter {
    String connectorCode();

    VendingCommandResult execute(VendingCommand command);

    record VendingCommand(
            long merchantId,
            long deviceId,
            String externalDeviceId,
            String commandReference,
            String commandType,
            Map<String, String> parameters) {}

    record VendingCommandResult(
            boolean success, String providerReference, String status, String message) {}
}
