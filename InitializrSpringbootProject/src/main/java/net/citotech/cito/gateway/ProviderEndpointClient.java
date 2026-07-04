package net.citotech.cito.gateway;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import net.citotech.cito.Model.GateWayResponse;

public final class ProviderEndpointClient {
    private ProviderEndpointClient() {}

    public static GateWayResponse execute(String channelCode, String displayName, String operation, PaymentGatewayRequest request) {
        String url = request.getMetadata().get(operation.toLowerCase() + "Url");
        String mode = request.getMetadata().getOrDefault("gatewayState", "SANDBOX");
        if (isBlank(url)) {
            if ("PRODUCTION".equalsIgnoreCase(mode)) {
                throw new PaymentGatewayException("Provider endpoint is required for production channel execution");
            }
            return accepted(channelCode, displayName, operation, request, "Sandbox endpoint not configured");
        }
        try {
            String body = jsonBody(channelCode, operation, request);
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("X-CPay-Channel", channelCode);
            connection.setRequestProperty("X-CPay-Reference", request.getReference());
            String headerName = request.getMetadata().get("authHeaderName");
            String headerValue = request.getMetadata().get("authHeaderValue");
            if (!isBlank(headerName) && !isBlank(headerValue)) connection.setRequestProperty(headerName, headerValue);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            String responseBody = read(status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream());
            GateWayResponse response = new GateWayResponse();
            response.setStatus(status >= 200 && status < 300 ? "SUCCESS" : "FAILED");
            response.setTransactionStatus(status >= 200 && status < 300 ? "SUBMITTED" : "FAILED");
            response.setHttpStatus(String.valueOf(status));
            response.setMessage(displayName + " " + operation + " provider endpoint response");
            response.setNetworkId(channelCode + "-" + request.getReference());
            response.setRequestTrace("requestHash=" + sha256(body));
            response.setResponseTrace(truncate(responseBody));
            return response;
        } catch (Exception e) {
            throw new PaymentGatewayException("Provider endpoint execution failed: " + e.getMessage());
        }
    }

    private static GateWayResponse accepted(String channelCode, String displayName, String operation, PaymentGatewayRequest request, String reason) {
        GateWayResponse response = new GateWayResponse();
        response.setStatus("SUCCESS");
        response.setTransactionStatus("SUBMITTED");
        response.setMessage(displayName + " " + operation + " accepted: " + reason);
        response.setHttpStatus("202");
        response.setNetworkId(channelCode + "-" + request.getReference());
        return response;
    }

    private static String jsonBody(String channelCode, String operation, PaymentGatewayRequest request) {
        return "{"
                + "\"channelCode\":\"" + esc(channelCode) + "\","
                + "\"operation\":\"" + esc(operation) + "\","
                + "\"merchantNumber\":\"" + esc(request.getMerchantNumber()) + "\","
                + "\"accountIdentifier\":\"" + esc(request.getAccountIdentifier()) + "\","
                + "\"amount\":" + request.getAmount() + ","
                + "\"reference\":\"" + esc(request.getReference()) + "\","
                + "\"description\":\"" + esc(request.getDescription()) + "\","
                + "\"callbackUrl\":\"" + esc(request.getCallbackUrl()) + "\""
                + "}";
    }

    private static String read(InputStream inputStream) throws Exception {
        if (inputStream == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private static String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String truncate(String value) {
        if (value == null) return "";
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private static boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
    private static String esc(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
