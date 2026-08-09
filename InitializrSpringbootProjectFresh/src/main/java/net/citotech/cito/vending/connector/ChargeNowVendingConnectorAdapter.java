package net.citotech.cito.vending.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.citotech.cito.Common;
import net.citotech.cito.Model.HttpRequestResponse;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.vending.connector.VendingConnectorConfigurationService.Contract;
import net.citotech.cito.vending.connector.VendingConnectorConfigurationService.Operation;
import org.springframework.stereotype.Component;

/**
 * Production HTTP adapter for ChargeNow/Bajie OEM hardware.
 *
 * <p>ChargeNow's public material confirms cloud-connected stations, remote unlock commands and
 * real-time status synchronization, while the partner wire contract is not publicly documented.
 * The adapter therefore implements the real HTTP/authentication/correlation mechanics and loads
 * each OEM operation's exact method, path, JSON template and response mappings from tenant-owned
 * configuration. Nothing in this class invents a private ChargeNow URL or field name.
 *
 * <p>JSON request templates may use {@code {{externalDeviceId}}}, {@code {{commandReference}}},
 * {@code {{rentalReference}}}, {@code {{merchantId}}}, {@code {{deviceId}}}, plus any command
 * parameter supplied by CPay. Authentication signing templates additionally support
 * {@code {{timestamp}}}, {@code {{method}}}, {@code {{path}}} and {@code {{body}}}.
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
        Contract contract = configurations.require(command.merchantId(), connectorCode());
        Operation operation =
                configurations.requireOperation(
                        command.merchantId(), connectorCode(), command.commandType());
        if (command.externalDeviceId() == null || command.externalDeviceId().isBlank()) {
            throw new PaymentGatewayException(
                    "ChargeNow manufacturer device id is required for " + command.commandType());
        }

        try {
            Map<String, String> values = templateValues(command);
            String body = renderBody(operation.requestTemplate(), values);
            String url = join(contract.commandBaseUrl(), operation.commandPath());
            Map<String, String> headers =
                    authHeaders(contract, command, operation, body, values);
            headers.put("Accept", "application/json");
            if (!body.isBlank()) headers.put("Content-Type", "application/json");
            if (!operation.idempotencyHeaderName().isBlank()) {
                headers.put(operation.idempotencyHeaderName(), command.commandReference());
            }

            HttpRequestResponse response =
                    Common.doHttpRequest(operation.httpMethod(), url, body, headers);
            boolean httpOk = response.getStatusCode() >= 200 && response.getStatusCode() < 300;
            String responseBody = response.getResponse() == null ? "" : response.getResponse();
            JsonNode responseJson = parseJson(responseBody);
            boolean contractOk = operationSuccess(operation, responseJson);
            boolean success = httpOk && contractOk;
            String reference = valueAt(responseJson, operation.responseReferenceField());
            if (reference.isBlank() && success) reference = command.commandReference();
            String message = valueAt(responseJson, operation.responseMessageField());
            if (message.isBlank()) {
                message =
                        success
                                ? "Manufacturer command accepted"
                                : safeFailure(response, operation.commandType());
            }
            String resultStatus =
                    success
                            ? ("IMMEDIATE".equals(operation.completionMode())
                                    ? "COMPLETED"
                                    : "ACCEPTED")
                            : "FAILED";
            return new VendingCommandResult(success, reference, resultStatus, message);
        } catch (PaymentGatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new PaymentGatewayException(
                    "Unable to execute ChargeNow vending command: " + command.commandType());
        }
    }

    private Map<String, String> templateValues(VendingCommand command) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("externalDeviceId", nullToEmpty(command.externalDeviceId()));
        values.put("commandReference", nullToEmpty(command.commandReference()));
        values.put("merchantId", String.valueOf(command.merchantId()));
        values.put("deviceId", String.valueOf(command.deviceId()));
        if (command.parameters() != null) {
            command.parameters().forEach(
                    (key, value) -> values.put(key, value == null ? "" : value));
        }
        values.putIfAbsent("rentalReference", "");
        return values;
    }

    private String renderBody(String template, Map<String, String> values) throws Exception {
        if (template == null || template.isBlank()) return "";
        JsonNode parsed = mapper.readTree(template);
        if (parsed == null || (!parsed.isObject() && !parsed.isArray())) {
            throw new PaymentGatewayException("Manufacturer requestTemplate must be a JSON object or array");
        }
        return mapper.writeValueAsString(substitute(parsed.deepCopy(), values));
    }

    private JsonNode substitute(JsonNode node, Map<String, String> values) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            object.fields()
                    .forEachRemaining(
                            entry ->
                                    object.set(
                                            entry.getKey(),
                                            substitute(entry.getValue(), values)));
            return object;
        }
        if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                array.set(i, substitute(array.get(i), values));
            }
            return array;
        }
        if (node.isTextual()) {
            return mapper.getNodeFactory().textNode(replace(node.asText(), values));
        }
        return node;
    }

    private Map<String, String> authHeaders(
            Contract contract,
            VendingCommand command,
            Operation operation,
            String body,
            Map<String, String> values)
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
            String header =
                    contract.authHeaderName().isBlank()
                            ? "X-API-Key"
                            : contract.authHeaderName();
            headers.put(header, contract.authValue());
            return headers;
        }
        if ("BASIC".equals(mode)) {
            requireSecret(contract.authValue(), "authValue");
            requireSecret(contract.authSecret(), "authSecret");
            String token =
                    Base64.getEncoder()
                            .encodeToString(
                                    (contract.authValue() + ":" + contract.authSecret())
                                            .getBytes(StandardCharsets.UTF_8));
            headers.put("Authorization", "Basic " + token);
            return headers;
        }
        if ("HMAC_SHA256_TS_BODY".equals(mode)) {
            requireSecret(contract.authSecret(), "authSecret");
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            Map<String, String> signingValues = new LinkedHashMap<>(values);
            signingValues.put("timestamp", timestamp);
            signingValues.put("method", operation.httpMethod());
            signingValues.put("path", operation.commandPath());
            signingValues.put("body", body);
            String signingTemplate =
                    contract.authSigningTemplate().isBlank()
                            ? "{{timestamp}}\n{{commandReference}}\n{{body}}"
                            : contract.authSigningTemplate();
            String signature =
                    encodeHmac(
                            contract.authSecret(),
                            replace(signingTemplate, signingValues),
                            contract.authSignatureEncoding());
            headers.put(
                    contract.authTimestampHeader().isBlank()
                            ? "X-CPay-Vending-Timestamp"
                            : contract.authTimestampHeader(),
                    timestamp);
            headers.put(
                    contract.authHeaderName().isBlank()
                            ? "X-CPay-Vending-Signature"
                            : contract.authHeaderName(),
                    signature);
            if (!contract.authValue().isBlank()) {
                headers.put(
                        contract.authKeyHeader().isBlank()
                                ? "X-CPay-Vending-Key"
                                : contract.authKeyHeader(),
                        contract.authValue());
            }
            return headers;
        }
        throw new PaymentGatewayException("Unsupported manufacturer auth mode: " + mode);
    }

    private boolean operationSuccess(Operation operation, JsonNode body) {
        if (operation.responseSuccessField().isBlank()) return true;
        String actual = valueAt(body, operation.responseSuccessField());
        if (operation.responseSuccessValue().isBlank()) {
            return "TRUE".equalsIgnoreCase(actual)
                    || "SUCCESS".equalsIgnoreCase(actual)
                    || "OK".equalsIgnoreCase(actual)
                    || "ACCEPTED".equalsIgnoreCase(actual)
                    || "0".equals(actual)
                    || "200".equals(actual);
        }
        return operation.responseSuccessValue().equalsIgnoreCase(actual);
    }

    private JsonNode parseJson(String body) {
        if (body == null || body.isBlank()) return mapper.createObjectNode();
        try {
            JsonNode parsed = mapper.readTree(body);
            return parsed == null ? mapper.createObjectNode() : parsed;
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

    private String safeFailure(HttpRequestResponse response, String commandType) {
        if (response.getStatusCode() > 0) {
            return commandType + " failed with manufacturer HTTP " + response.getStatusCode();
        }
        String transport = response.getErrorMessage();
        return transport == null || transport.isBlank()
                ? commandType + " could not be delivered to manufacturer"
                : commandType + " transport failed: " + truncate(transport, 240);
    }

    private String join(String base, String path) {
        String left = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String right = path.startsWith("/") ? path : "/" + path;
        return left + right;
    }

    private String replace(String template, Map<String, String> values) {
        String result = template == null ? "" : template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result =
                    result.replace(
                            "{{" + entry.getKey() + "}}",
                            entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }

    private void requireSecret(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new PaymentGatewayException(
                    "Manufacturer connector " + name + " is not configured");
        }
    }

    private String encodeHmac(String secret, String value, String encoding) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        return "HEX".equalsIgnoreCase(encoding)
                ? HexFormat.of().formatHex(digest)
                : Base64.getEncoder().encodeToString(digest);
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
