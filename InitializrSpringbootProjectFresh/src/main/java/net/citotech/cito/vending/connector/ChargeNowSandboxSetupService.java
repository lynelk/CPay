package net.citotech.cito.vending.connector;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applies a complete ChargeNow OEM sandbox contract as one tenant-scoped configuration bundle. */
@Service
public class ChargeNowSandboxSetupService {
    private static final String CONNECTOR_CODE = "CHARGENOW";

    private final VendingConnectorConfigurationService configurations;
    private final VendingCallbackCorrelationService correlations;

    public ChargeNowSandboxSetupService(
            VendingConnectorConfigurationService configurations,
            VendingCallbackCorrelationService correlations) {
        this.configurations = configurations;
        this.correlations = correlations;
    }

    /**
     * Applies the connector, operation and callback-correlation contract in one transaction.
     * Cleartext credentials are accepted only in the request and are encrypted by the existing
     * connector configuration service before persistence.
     */
    @Transactional
    public Map<String, Object> apply(long merchantId, Map<String, Object> body) {
        Map<String, Object> connector = mutableRequiredMap(body.get("connector"), "connector");
        Map<String, Object> operationBundle =
                requiredMap(body.get("operations"), "operations");
        Map<String, Object> release =
                requiredMap(operationBundle.get("RELEASE_ASSET"), "operations.RELEASE_ASSET");

        copyIfBlank(connector, "releasePath", release.get("commandPath"));
        copyIfBlank(connector, "releaseRequestTemplate", release.get("requestTemplate"));
        copyIfBlank(connector, "releaseCompletionMode", release.get("completionMode"));
        copyIfBlank(connector, "idempotencyHeaderName", release.get("idempotencyHeaderName"));
        copyIfBlank(connector, "responseSuccessField", release.get("responseSuccessField"));
        copyIfBlank(connector, "responseSuccessValue", release.get("responseSuccessValue"));
        copyIfBlank(connector, "responseReferenceField", release.get("responseReferenceField"));
        copyIfBlank(connector, "responseMessageField", release.get("responseMessageField"));

        configurations.save(merchantId, CONNECTOR_CODE, connector);
        for (Map.Entry<String, Object> entry : operationBundle.entrySet()) {
            String commandType = normalize(entry.getKey());
            if (commandType.isBlank()) {
                throw new PaymentGatewayException("Operation command type is required");
            }
            configurations.saveOperation(
                    merchantId,
                    CONNECTOR_CODE,
                    commandType,
                    requiredMap(entry.getValue(), "operations." + commandType));
        }

        Map<String, Object> correlation = optionalMap(body.get("callbackCorrelation"));
        String commandReferenceField = text(correlation.get("callbackCommandReferenceField"));
        String providerReferenceField = text(correlation.get("callbackProviderReferenceField"));
        correlations.save(
                merchantId,
                CONNECTOR_CODE,
                commandReferenceField,
                providerReferenceField);

        ensureCallbackCanCorrelate(connector, commandReferenceField, providerReferenceField);
        return manifest(merchantId);
    }

    /** Returns the redacted applied contract and the current CPay sandbox-readiness decision. */
    public Map<String, Object> manifest(long merchantId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("connectorCode", CONNECTOR_CODE);
        result.put(
                "callbackPath",
                "/api/v2/vending/device-callbacks/" + CONNECTOR_CODE + "/" + merchantId);
        result.put("readiness", configurations.readiness(merchantId, CONNECTOR_CODE));
        try {
            result.put("connector", configurations.view(merchantId, CONNECTOR_CODE));
            result.put("operations", configurations.operations(merchantId, CONNECTOR_CODE));
            VendingCallbackCorrelationService.Mapping mapping =
                    correlations.mapping(merchantId, CONNECTOR_CODE);
            result.put(
                    "callbackCorrelation",
                    Map.of(
                            "callbackCommandReferenceField",
                            mapping.commandReferenceField(),
                            "callbackProviderReferenceField",
                            mapping.providerReferenceField()));
        } catch (PaymentGatewayException e) {
            result.put("connector", Map.of());
            result.put("operations", java.util.List.of());
            result.put("callbackCorrelation", Map.of());
        }
        return result;
    }

    private void ensureCallbackCanCorrelate(
            Map<String, Object> connector,
            String commandReferenceField,
            String providerReferenceField) {
        String rentalField = text(connector.get("callbackRentalField"));
        if (rentalField.isBlank()
                && commandReferenceField.isBlank()
                && providerReferenceField.isBlank()) {
            throw new PaymentGatewayException(
                    "ChargeNow callback setup must map a rental, command, or provider reference field");
        }
    }

    private void copyIfBlank(Map<String, Object> target, String key, Object value) {
        if (text(target.get(key)).isBlank() && !text(value).isBlank()) {
            target.put(key, value);
        }
    }

    private Map<String, Object> mutableRequiredMap(Object value, String name) {
        return new LinkedHashMap<>(requiredMap(value, name));
    }

    private Map<String, Object> requiredMap(Object value, String name) {
        Map<String, Object> result = optionalMap(value);
        if (result.isEmpty()) {
            throw new PaymentGatewayException(name + " is required");
        }
        return result;
    }

    private Map<String, Object> optionalMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach(
                (key, item) -> {
                    if (key != null) result.put(String.valueOf(key), item);
                });
        return result;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
