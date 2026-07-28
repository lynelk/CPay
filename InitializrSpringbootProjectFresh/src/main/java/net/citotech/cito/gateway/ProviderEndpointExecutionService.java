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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProviderEndpointExecutionService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ProviderTokenStoreService tokenStoreService;

    public ProviderEndpointExecutionService(NamedParameterJdbcTemplate jdbcTemplate,
                                            ProviderTokenStoreService tokenStoreService) {
        this.jdbcTemplate = jdbcTemplate;
        this.tokenStoreService = tokenStoreService;
    }

    public GateWayResponse execute(String channelCode, String displayName, String operation, PaymentGatewayRequest request) {
        String endpointKey = operation.toLowerCase() + "Url";
        String endpointUrl = request.getMetadata().get(endpointKey);
        String gatewayState = request.getMetadata().getOrDefault("gatewayState", "SANDBOX");
        if (isBlank(endpointUrl)) {
            if ("PRODUCTION".equalsIgnoreCase(gatewayState)) {
                throw new PaymentGatewayException("Provider endpoint URL is required in production mode for " + channelCode + " " + operation);
            }
            SandboxScenario scenario = sandboxScenario(operation, request.getAccountIdentifier());
            GateWayResponse accepted = response(channelCode, displayName, operation, request, scenario.httpStatus, scenario.transactionStatus, scenario.message);
            record(channelCode, operation, request, endpointKey, scenario.httpStatus, scenario.runStatus, accepted.getMessage());
            return accepted;
        }
        String body = body(channelCode, operation, request);
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(endpointUrl).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("X-CPay-Channel", channelCode);
            connection.setRequestProperty("X-CPay-Reference", request.getReference());
            tokenStoreService.findValid(channelCode, operation, gatewayState)
                .ifPresent(token -> connection.setRequestProperty("Authorization", "Bearer " + token.getTokenValue()));
            String headerName = request.getMetadata().get("authHeaderName");
            String headerValue = request.getMetadata().get("authHeaderValue");
            if (!isBlank(headerName) && !isBlank(headerValue)) {
                connection.setRequestProperty(headerName, headerValue);
            }
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int httpStatus = connection.getResponseCode();
            String responseBody = read(httpStatus >= 200 && httpStatus < 300 ? connection.getInputStream() : connection.getErrorStream());
            String status = httpStatus >= 200 && httpStatus < 300 ? "SUBMITTED" : "FAILED";
            record(channelCode, operation, request, endpointUrl, httpStatus, status, responseBody);
            GateWayResponse result = response(channelCode, displayName, operation, request, httpStatus, status, trim(responseBody));
            result.setRequestTrace("requestHash=" + hash(body));
            return result;
        } catch (Exception e) {
            record(channelCode, operation, request, endpointUrl, 0, "FAILED", e.getMessage());
            throw new PaymentGatewayException("Provider endpoint execution failed: " + e.getMessage());
        }
    }

    private GateWayResponse response(String channelCode, String displayName, String operation, PaymentGatewayRequest request, int httpStatus, String status, String message) {
        GateWayResponse response = new GateWayResponse();
        response.setStatus("FAILED".equals(status) ? "FAILED" : "SUCCESS");
        response.setTransactionStatus(status);
        response.setHttpStatus(String.valueOf(httpStatus));
        response.setMessage(displayName + " " + operation + " provider response: " + message);
        response.setNetworkId(channelCode + "-" + request.getReference());
        return response;
    }

    private void record(String channelCode, String operation, PaymentGatewayRequest request, String endpointUrl, int httpStatus, String status, String message) {
        String sql = "INSERT INTO provider_endpoint_runs (channel_code, operation_name, reference_value, endpoint_url, http_status, request_hash, response_summary, run_status, merchant_number, environment) VALUES (:channel_code, :operation_name, :reference_value, :endpoint_url, :http_status, :request_hash, :response_summary, :run_status, :merchant_number, :environment)";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("channel_code", channelCode);
        p.addValue("operation_name", operation);
        p.addValue("reference_value", request.getReference());
        p.addValue("endpoint_url", endpointUrl);
        p.addValue("http_status", httpStatus);
        p.addValue("request_hash", safeHash(channelCode + ":" + operation + ":" + request.getReference()));
        p.addValue("response_summary", trim(message));
        p.addValue("run_status", status);
        p.addValue("merchant_number", request.getMerchantNumber());
        p.addValue("environment", request.getMetadata().getOrDefault("credentialEnvironment", request.getMetadata().getOrDefault("gatewayState", "SANDBOX")));
        try { jdbcTemplate.update(sql, p); } catch (Exception ignored) { }
    }

    private SandboxScenario sandboxScenario(String operation, String accountIdentifier) {
        String account = accountIdentifier == null ? "" : accountIdentifier.trim();
        String op = operation == null ? "" : operation.trim().toUpperCase();
        if ("COLLECT".equals(op)) {
            if (account.endsWith("000002")) return new SandboxScenario(202, "FAILED", "SANDBOX_FAILED", "Sandbox customer declined the collection");
            if (account.endsWith("000003")) return new SandboxScenario(202, "PENDING", "SANDBOX_PENDING", "Sandbox collection remains pending");
            if (account.endsWith("000004")) return new SandboxScenario(202, "UNKNOWN", "SANDBOX_TIMEOUT", "Sandbox provider timeout scenario");
            if (account.endsWith("000005")) return new SandboxScenario(400, "FAILED", "SANDBOX_REJECTED", "Sandbox account is unsupported");
            return new SandboxScenario(202, "SUCCESSFUL", "SANDBOX_ACCEPTED", "Sandbox collection accepted and resolved successfully");
        }
        if ("PAYOUT".equals(op)) {
            if (account.endsWith("000002")) return new SandboxScenario(202, "FAILED", "SANDBOX_FAILED", "Sandbox payout failed");
            return new SandboxScenario(202, "SUCCESSFUL", "SANDBOX_ACCEPTED", "Sandbox payout accepted and resolved successfully");
        }
        return new SandboxScenario(202, "SUBMITTED", "SANDBOX_ACCEPTED", "Sandbox endpoint not configured");
    }

    private static class SandboxScenario {
        private final int httpStatus;
        private final String transactionStatus;
        private final String runStatus;
        private final String message;

        private SandboxScenario(int httpStatus, String transactionStatus, String runStatus, String message) {
            this.httpStatus = httpStatus;
            this.transactionStatus = transactionStatus;
            this.runStatus = runStatus;
            this.message = message;
        }
    }

    private String body(String channelCode, String operation, PaymentGatewayRequest request) {
        return "{\"channelCode\":\"" + esc(channelCode) + "\",\"operation\":\"" + esc(operation) + "\",\"merchantNumber\":\"" + esc(request.getMerchantNumber()) + "\",\"accountIdentifier\":\"" + esc(request.getAccountIdentifier()) + "\",\"amount\":" + request.getAmount() + ",\"reference\":\"" + esc(request.getReference()) + "\",\"description\":\"" + esc(request.getDescription()) + "\",\"callbackUrl\":\"" + esc(request.getCallbackUrl()) + "\"}";
    }

    private String read(InputStream inputStream) throws Exception {
        if (inputStream == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
            return builder.toString();
        }
    }

    private String hash(String value) throws Exception { return safeHash(value); }
    private String safeHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) builder.append(String.format("%02x", b));
            return builder.toString();
        } catch (Exception e) {
            return "hash-error";
        }
    }

    private String trim(String value) { return value == null ? "" : (value.length() <= 1000 ? value : value.substring(0, 1000)); }
    private boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
    private String esc(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\""); }
}

