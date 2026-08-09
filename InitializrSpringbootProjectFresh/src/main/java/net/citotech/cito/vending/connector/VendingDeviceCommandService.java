package net.citotech.cito.vending.connector;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.vending.VendingRepository;
import org.springframework.stereotype.Service;

/**
 * Operator-side manufacturer command runner for non-financial, non-eject diagnostics.
 *
 * <p>Physical release remains exclusively owned by {@code VendingRentalService} after a successful
 * CPay collection. This service intentionally whitelists read-only/probe command names so a portal
 * user cannot turn the generic OEM adapter into an unaudited dispense endpoint.
 */
@Service
public class VendingDeviceCommandService {
    private static final Set<String> SAFE_COMMANDS =
            Set.of("QUERY_STATUS", "GET_STATUS", "PING", "INVENTORY_STATUS");

    private final VendingRepository repository;
    private final VendingConnectorRegistry connectors;

    public VendingDeviceCommandService(
            VendingRepository repository, VendingConnectorRegistry connectors) {
        this.repository = repository;
        this.connectors = connectors;
    }

    public Map<String, Object> probe(
            long merchantId,
            String deviceCode,
            String commandType,
            Map<String, String> parameters,
            String actor) {
        String type =
                normalize(
                        commandType == null || commandType.isBlank()
                                ? "QUERY_STATUS"
                                : commandType);
        if (!SAFE_COMMANDS.contains(type)) {
            throw new PaymentGatewayException(
                    "Only read-only manufacturer probe operations are allowed from this endpoint");
        }
        Map<String, Object> device = repository.deviceByCode(merchantId, deviceCode);
        String connectorCode = VendingRepository.string(device.get("connector_code"));
        long deviceId = VendingRepository.number(device.get("id"));
        String commandReference =
                "VEND-PROBE-" + type + "-" + UUID.randomUUID().toString().replace("-", "");
        String requestEvidence =
                "{\"deviceCode\":\""
                        + json(deviceCode)
                        + "\",\"commandType\":\""
                        + json(type)
                        + "\"}";
        if (!repository.claimCommand(
                merchantId,
                deviceId,
                null,
                commandReference,
                type,
                connectorCode,
                requestEvidence)) {
            throw new PaymentGatewayException("Manufacturer probe command could not be claimed");
        }

        try {
            VendingConnectorAdapter.VendingCommandResult result =
                    connectors
                            .require(connectorCode)
                            .execute(
                                    new VendingConnectorAdapter.VendingCommand(
                                            merchantId,
                                            deviceId,
                                            VendingRepository.string(
                                                    device.get("external_device_id")),
                                            commandReference,
                                            type,
                                            parameters == null ? Map.of() : parameters));
            repository.completeCommand(
                    merchantId,
                    commandReference,
                    result.status(),
                    result.providerReference(),
                    "{\"message\":\"" + json(result.message()) + "\"}");
            repository.event(
                    merchantId,
                    "MANUFACTURER_PROBE_" + (result.success() ? "SUCCEEDED" : "FAILED"),
                    "DEVICE",
                    deviceCode,
                    actor,
                    null,
                    null,
                    "{\"commandType\":\""
                            + json(type)
                            + "\",\"providerReference\":\""
                            + json(result.providerReference())
                            + "\"}");
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("deviceCode", deviceCode);
            response.put("connectorCode", connectorCode);
            response.put("commandType", type);
            response.put("commandReference", commandReference);
            response.put("success", result.success());
            response.put("status", result.status());
            response.put("providerReference", result.providerReference());
            response.put("message", result.message());
            return response;
        } catch (RuntimeException e) {
            repository.completeCommand(
                    merchantId,
                    commandReference,
                    "FAILED",
                    null,
                    "{\"message\":\"" + json(safeMessage(e)) + "\"}");
            throw e;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String safeMessage(RuntimeException e) {
        String value = e.getMessage();
        if (value == null || value.isBlank()) return e.getClass().getSimpleName();
        return value.length() <= 240 ? value : value.substring(0, 240);
    }

    private String json(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
