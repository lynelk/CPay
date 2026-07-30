package net.citotech.cito.gateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import net.citotech.cito.Common;
import net.citotech.cito.Model.GateWayResponse;
import net.citotech.cito.Model.HttpRequestResponse;

public final class ProviderEndpointClient {
    private ProviderEndpointClient() {}

    public static GateWayResponse execute(
            String channelCode,
            String displayName,
            String operation,
            PaymentGatewayRequest request) {
        String endpoint = request.getMetadata().get(operation.toLowerCase() + "Url");
        String mode = request.getMetadata().getOrDefault("gatewayState", "SANDBOX");
        if (isBlank(endpoint)) {
            if ("PRODUCTION".equalsIgnoreCase(mode))
                throw new PaymentGatewayException(
                        "Provider endpoint URL is required in production mode");
            return accepted(
                    channelCode,
                    displayName,
                    operation,
                    request,
                    "Sandbox endpoint URL not configured");
        }
        try {
            String payload = jsonPayload(channelCode, operation, request);
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("X-CPay-Channel", channelCode);
            headers.put("X-CPay-Reference", request.getReference());
            addOptionalHeader(headers, request, "authHeaderName", "authHeaderValue");
            HttpRequestResponse httpResponse = Common.doHttpRequest("POST", endpoint, payload, headers);
            int code = httpResponse.getStatusCode();
            boolean ok = code >= 200 && code < 300;
            String body = httpResponse.getResponse() == null ? "" : httpResponse.getResponse();
            GateWayResponse result = new GateWayResponse();
            result.setStatus(ok ? "SUCCESS" : "FAILED");
            result.setTransactionStatus(ok ? "SUBMITTED" : "FAILED");
            result.setHttpStatus(String.valueOf(code));
            // Audit C6: `body` is the RAW, unfiltered provider response - never hand it to a
            // merchant
            // directly on failure. Unlike ProviderEndpointExecutionService, this class has no
            // separate
            // DB-backed run log, so the raw body is kept in requestTrace (internal-only - never
            // serialized into a merchant-facing response, see GateWayResponse/PaymentResult)
            // instead of
            // being dropped entirely.
            String merchantMessage =
                    ok
                            ? displayName
                                    + " "
                                    + operation
                                    + " endpoint response: "
                                    + truncate(body)
                            : ProviderErrorTranslator.translateProviderResponse(code, body)
                                    .merchantMessage();
            result.setMessage(merchantMessage);
            result.setNetworkId(channelCode + "-" + request.getReference());
            result.setRequestTrace(
                    "requestHash="
                            + sha256(payload)
                            + (ok ? "" : " | rawResponse=" + truncate(body)));
            return result;
        } catch (Exception e) {
            throw new PaymentGatewayException(
                    "Provider endpoint execution failed: " + e.getMessage());
        }
    }

    private static void addOptionalHeader(
            Map<String, String> headers,
            PaymentGatewayRequest request,
            String nameKey,
            String valueKey) {
        String name = request.getMetadata().get(nameKey);
        String value = request.getMetadata().get(valueKey);
        if (!isBlank(name) && !isBlank(value)) headers.put(name, value);
    }

    private static GateWayResponse accepted(
            String channelCode,
            String displayName,
            String operation,
            PaymentGatewayRequest request,
            String reason) {
        GateWayResponse result = new GateWayResponse();
        result.setStatus("SUCCESS");
        result.setTransactionStatus("SUBMITTED");
        result.setHttpStatus("202");
        result.setMessage(displayName + " " + operation + " accepted: " + reason);
        result.setNetworkId(channelCode + "-" + request.getReference());
        return result;
    }

    private static String jsonPayload(
            String channelCode, String operation, PaymentGatewayRequest request) {
        return "{"
                + "\"channelCode\":\""
                + escape(channelCode)
                + "\","
                + "\"operation\":\""
                + escape(operation)
                + "\","
                + "\"merchantNumber\":\""
                + escape(request.getMerchantNumber())
                + "\","
                + "\"accountIdentifier\":\""
                + escape(request.getAccountIdentifier())
                + "\","
                + "\"amount\":"
                + request.getAmount()
                + ","
                + "\"reference\":\""
                + escape(request.getReference())
                + "\","
                + "\"description\":\""
                + escape(request.getDescription())
                + "\","
                + "\"callbackUrl\":\""
                + escape(request.getCallbackUrl())
                + "\""
                + "}";
    }

    private static String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        for (byte b : hash) builder.append(String.format("%02x", b));
        return builder.toString();
    }

    private static String truncate(String value) {
        return value == null ? "" : (value.length() <= 1000 ? value : value.substring(0, 1000));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
