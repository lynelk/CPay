package net.citotech.cito.vending.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.citotech.cito.Common;
import net.citotech.cito.Model.HttpRequestResponse;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.vending.connector.VendingConnectorConfigurationService.Contract;
import org.springframework.stereotype.Component;

/**
 * Production HTTP adapter for ChargeNow/Bajie OEM hardware.
 *
 * <p>No unpublished manufacturer endpoint, signature scheme or JSON field is hard-coded. The real
 * commercial API contract is supplied per tenant through {@code vending_connector_configs}. This
 * lets CPay control real cabinets immediately after the OEM provides the integration pack without
 * teaching the rental state machine vendor-specific wire details.
 *
 * <p>The release request is a JSON template supplied by the OEM/operator. String values may contain
 * {@code {{externalDeviceId}}}, {@code {{commandReference}}}, {@code {{rentalReference}}},
 * {@code {{merchantId}}}, or {@code {{deviceId}}}. Replacement happens after JSON parsing, so values
 * remain correctly escaped.
 */
@Component
public class ChargeNowVendingConnectorAdapter implements VendingConnectorAdapter {
    private final VendingConnectorConfigurationService configurations;
    private final ObjectMapper mapper;

    public ChargeNowVendingConnectorAdapter(
            VendingConnectorConfigurationService configurations, ObjectMapper mapper) {
        this.configurations = configurations;
        this.mapper = mapper;
    }

    @Override
    public String connectorCode() {
        return "CHARGENOW";
    }

    @Override
    public VendingCommandResult execute(VendingCommand command) {
        if (!"RELEASE_ASSET".equalsIgnoreCase(command.commandType())) {
            return new VendingCommandResult(
                    false,
                    "",
                    "UNSUPPORTED",
                    "ChargeNow connector has no configured mapping for " + command.commandType());
        }
        Contract contract = configurations.require(command.merchantId(), connectorCode());
        try {
            String body = renderReleaseBody(contract, command);
            Map<String, String> headers = authHeaders(contract, command.commandReference(), body);
            headers.put("Content-Type", "application/json");
            headers.put("Accept", "application/json");
            String url = join(contract.commandBaseUrl(), contract.releasePath());
            HttpRequestResponse response = Common.doHttpRequest("POST", url, body, headers);
            boolean httpOk = response.getStatusCode() >= 200 && response.getStatusCode() < 300;
            String responseBody = response.getResponse() == null ? "" : response.getResponse();
            JsonNode responseJson = parseObject(responseBody);
            boolean contractOk = contractSuccess(contract, responseJson);
            boolean success = httpOk && contractOk;
            String reference = valueAt(responseJson, contract.responseReferenceField());
            String message = valueAt(responseJson, contract.responseMessageField());
            if (message.isBlank()) {
                message = success
                        ? "Manufacturer release command accepted"
                        : safeFailure(response);
            }
            return new VendingCommandResult(
                    success,
                    reference,
                    success ? "ACCEPTED" : "FAILED",
                    message);
        } catch (PaymentGatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new PaymentGatewayException("Unable to execute ChargeNow vending command");
        }
    }

    private String renderReleaseBody(Contract contract, VendingCommand command) throws Exception {
        JsonNode template = mapper.readTree(contract.releaseRequestTemplate());
        if (template == null || (!template.isObject() && !template.isArray())) {
            throw new PaymentGatewayException("Manufacturer releaseRequestTemplate must be JSON");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put("externalDeviceId", nullToEmpty(command.externalDeviceId()));
        values.put("commandReference", command.commandReference());
        values.put("rentalReference", command.parameters().getOrDefault("rentalReference", ""));
        values.put("merchantId", String.valueOf(command.merchantId()));
        values.put("deviceId", String.valueOf(command.deviceId()));
        return mapper.writeValueAsString(substitute(template.deepCopy(), values));
    }

    private JsonNode substitute(JsonNode node, Map<String, String> values) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            object.fields().forEachRemaining(entry -> object.set(entry.getKey(), substitute(entry.getValue(), values)));
            return object;
        }
        if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) array.set(i, substitute(array.get(i), values));
            return array;
        }
        if (node.isTextual()) {
            String value = node.asText();
            for (Map.Entry<String, String> entry : values.entrySet()) {
                value = value.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
            return mapper.getNodeFactory().textNode(value);
        }
        return node;
    }

    private Map<String, String> authHeaders(Contract contract, String reference, String body)
            throws Exception {
        Map<String, String> headers = new LinkedHashMap<>();
        String mode = contract.authMode();
        if ("NONE".equals(mode)) return headers;
        if ("BEARER".equals(mode)) {
            requireSecret(contract.authValue(), "authValue");
            headers.put("Authorization", "Bearer " + contract.authValue());
            return headers;
        }
        if ("API_KEY_HEADER".equals(mode)) {
            requireSecret(contract.authValue(), "authValue");
            String header = contract.authHeaderName().isBlank() ? "X-API-Key" : contract.authHeaderName();
            headers.put(header, contract.authValue());
            return headers;
        }
        if ("BASIC".equals(mode)) {
            requireSecret(contract.authValue(), "authValue");
            requireSecret(contract.authSecret(), "authSecret");
            String token = Base64.getEncoder().encodeToString(
                    (contract.authValue() + ":" + contract.authSecret()).getBytes(StandardCharsets.UTF_8));
            headers.put("Authorization", "Basic " + token);
            return headers;
        }
        if ("HMAC_SHA256_TS_BODY".equals(mode)) {
            requireSecret(contract.authSecret(), "authSecret");
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            String base = timestamp + "\n" + reference + "\n" + body;
            String signature = hmac(contract.authSecret(), base);
            headers.put("X-CPay-Vending-Timestamp", timestamp);
            headers.put(
                    contract.authHeaderName().isBlank()
                            ? "X-CPay-Vending-Signature"
                            : contract.authHeaderName(),
                    signature);
            if (!contract.authValue().isBlank()) headers.put("X-CPay-Vending-Key", contract.authValue());
            return headers;
        }
        throw new PaymentGatewayException("Unsupported manufacturer auth mode: " + mode);
    }

    private boolean contractSuccess(Contract contract, JsonNode body) {
        if (contract.responseSuccessField().isBlank()) return true;
        String actual = valueAt(body, contract.responseSuccessField());
        if (contract.responseSuccessValue().isBlank()) {
            return "TRUE".equalsIgnoreCase(actual)
                    || "SUCCESS".equalsIgnoreCase(actual)
                    || "OK".equalsIgnoreCase(actual)
                    || "ACCEPTED".equalsIgnoreCase(actual)
                    || "0".equals(actual)
                    || "200".equals(actual);
        }
        return contract.responseSuccessValue().equalsIgnoreCase(actual);
    }

    private JsonNode parseObject(String body) {
        if (body == null || body.isBlank()) return mapper.createObjectNode();
        try {
            return mapper.readTree(body);
        } catch (Exception ignored) {
            return mapper.createObjectNode();
        }
    }

    private String valueAt(JsonNode node, String dottedPath) {
        if (node == null || dottedPath == null || dottedPath.isBlank()) return "";
        JsonNode current = node;
        for (String part : dottedPath.split("\\.")) {
            if (current == null) return "";
            current = current.get(part);
        }
        return current == null || current.isNull() ? "" : current.asText("");
    }

    private String safeFailure(HttpRequestResponse response) {
        if (response.getStatusCode() > 0) {
            return "Manufacturer command failed with HTTP " + response.getStatusCode();
        }
        return "Manufacturer command could not be delivered";
    }

    private String join(String base, String path) {
        String left = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String right = path.startsWith("/") ? path : "/" + path;
        return left + right;
    }

    private void requireSecret(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new PaymentGatewayException("Manufacturer connector " + name + " is not configured");
        }
    }

    private String hmac(String secret, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
