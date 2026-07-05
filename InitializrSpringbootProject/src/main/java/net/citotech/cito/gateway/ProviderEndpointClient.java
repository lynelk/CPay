package net.citotech.cito.gateway;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
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
            URI endpointUri = validateEndpoint(endpoint, request.getMetadata().get("allowedHosts"));
            String payload = jsonPayload(channelCode, operation, request);
            HttpURLConnection connection = (HttpURLConnection) endpointUri.toURL().openConnection();
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
            String body = read(code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream());
            GateWayResponse result = new GateWayResponse();
            result.setStatus(code >= 200 && code < 300 ? "SUCCESS" : "FAILED");
            result.setTransactionStatus(code >= 200 && code < 300 ? "SUBMITTED" : "FAILED");
            result.setHttpStatus(String.valueOf(code));
            result.setMessage(displayName + " " + operation + " endpoint response: " + truncate(body));
            result.setNetworkId(channelCode + "-" + request.getReference());
            result.setRequestTrace("requestHash=" + sha256(payload));
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

    private static URI validateEndpoint(String endpoint, String allowedHostsCsv) {
        URI uri = URI.create(endpoint);
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (isBlank(scheme) || (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))) {
            throw new PaymentGatewayException("Provider endpoint must use HTTP/HTTPS");
        }
        if (isBlank(host)) {
            throw new PaymentGatewayException("Provider endpoint host is invalid");
        }
        if (!isBlank(uri.getUserInfo())) {
            throw new PaymentGatewayException("Provider endpoint must not contain user info");
        }
        Set<String> allowedHosts = parseAllowedHosts(allowedHostsCsv);
        if (!allowedHosts.isEmpty() && !allowedHosts.contains(host.toLowerCase())) {
            throw new PaymentGatewayException("Provider endpoint host is not allowed");
        }
        return uri;
    }

    private static Set<String> parseAllowedHosts(String allowedHostsCsv) {
        Set<String> allowed = new HashSet<>();
        if (isBlank(allowedHostsCsv)) return allowed;
        String[] parts = allowedHostsCsv.split(",");
        for (String part : parts) {
            if (!isBlank(part)) allowed.add(part.trim().toLowerCase());
        }
        return allowed;
    }

    private static String truncate(String value) { return value == null ? "" : (value.length() <= 1000 ? value : value.substring(0, 1000)); }
    private static boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
    private static String escape(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
