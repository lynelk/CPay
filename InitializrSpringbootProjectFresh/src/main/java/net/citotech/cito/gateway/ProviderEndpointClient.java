package net.citotech.cito.gateway;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import net.citotech.cito.Model.GateWayResponse;

public final class ProviderEndpointClient {
    private ProviderEndpointClient() {}

    public static GateWayResponse execute(String channelCode, String displayName, String operation, PaymentGatewayRequest request) {
        String endpoint = request.getMetadata().get(operation.toLowerCase() + "Url");
        String mode = request.getMetadata().getOrDefault("gatewayState", "SANDBOX");
        if (isBlank(endpoint)) {
            if ("PRODUCTION".equalsIgnoreCase(mode)) throw new PaymentGatewayException("Provider endpoint URL is required in production mode");
            return accepted(channelCode, displayName, operation, request, "Sandbox endpoint URL not configured");
        }
        try {
            String payload = jsonPayload(channelCode, operation, request);
            HttpURLConnection connection = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("X-CPay-Channel", channelCode);
            connection.setRequestProperty("X-CPay-Reference", request.getReference());
            addOptionalHeader(connection, request, "authHeaderName", "authHeaderValue");
            try (OutputStream os = connection.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }
            int code = connection.getResponseCode();
            boolean ok = code >= 200 && code < 300;
            String body = read(ok ? connection.getInputStream() : connection.getErrorStream());
            GateWayResponse result = new GateWayResponse();
            result.setStatus(ok ? "SUCCESS" : "FAILED");
            result.setTransactionStatus(ok ? "SUBMITTED" : "FAILED");
            result.setHttpStatus(String.valueOf(code));
            // Audit C6: `body` is the RAW, unfiltered provider response - never hand it to a merchant
            // directly on failure. Unlike ProviderEndpointExecutionService, this class has no separate
            // DB-backed run log, so the raw body is kept in requestTrace (internal-only - never
            // serialized into a merchant-facing response, see GateWayResponse/PaymentResult) instead of
            // being dropped entirely.
            String merchantMessage = ok
                    ? displayName + " " + operation + " endpoint response: " + truncate(body)
                    : ProviderErrorTranslator.translateProviderResponse(code, body).merchantMessage();
            result.setMessage(merchantMessage);
            result.setNetworkId(channelCode + "-" + request.getReference());
            result.setRequestTrace("requestHash=" + sha256(payload) + (ok ? "" : " | rawResponse=" + truncate(body)));
            return result;
        } catch (Exception e) {
            throw new PaymentGatewayException("Provider endpoint execution failed: " + e.getMessage());
        }
    }

    private static void addOptionalHeader(HttpURLConnection connection, PaymentGatewayRequest request, String nameKey, String valueKey) {
        String name = request.getMetadata().get(nameKey);
        String value = request.getMetadata().get(valueKey);
        if (!isBlank(name) && !isBlank(value)) connection.setRequestProperty(name, value);
    }

    private static GateWayResponse accepted(String channelCode, String displayName, String operation, PaymentGatewayRequest request, String reason) {
        GateWayResponse result = new GateWayResponse();
        result.setStatus("SUCCESS");
        result.setTransactionStatus("SUBMITTED");
        result.setHttpStatus("202");
        result.setMessage(displayName + " " + operation + " accepted: " + reason);
        result.setNetworkId(channelCode + "-" + request.getReference());
        return result;
    }

    private static String jsonPayload(String channelCode, String operation, PaymentGatewayRequest request) {
        return "{" + "\"channelCode\":\"" + escape(channelCode) + "\"," + "\"operation\":\"" + escape(operation) + "\"," + "\"merchantNumber\":\"" + escape(request.getMerchantNumber()) + "\"," + "\"accountIdentifier\":\"" + escape(request.getAccountIdentifier()) + "\"," + "\"amount\":" + request.getAmount() + "," + "\"reference\":\"" + escape(request.getReference()) + "\"," + "\"description\":\"" + escape(request.getDescription()) + "\"," + "\"callbackUrl\":\"" + escape(request.getCallbackUrl()) + "\"" + "}";
    }

    private static String read(InputStream inputStream) throws Exception {
        if (inputStream == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
            return builder.toString();
        }
    }

    private static String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        for (byte b : hash) builder.append(String.format("%02x", b));
        return builder.toString();
    }

    private static String truncate(String value) { return value == null ? "" : (value.length() <= 1000 ? value : value.substring(0, 1000)); }
    private static boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
    private static String escape(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\""); }
}

