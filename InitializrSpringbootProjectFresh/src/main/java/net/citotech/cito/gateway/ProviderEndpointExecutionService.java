package net.citotech.cito.gateway;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import net.citotech.cito.Model.GateWayResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProviderEndpointExecutionService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ProviderEndpointExecutionService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public GateWayResponse execute(String channelCode, String displayName, String operation, PaymentGatewayRequest request) {
        String endpointKey = operation.toLowerCase() + "Url";
        String endpointUrl = request.getMetadata().get(endpointKey);
        String gatewayState = request.getMetadata().getOrDefault("gatewayState", "SANDBOX");
        if (isBlank(endpointUrl)) {
            if ("PRODUCTION".equalsIgnoreCase(gatewayState)) {
                throw new PaymentGatewayException("Provider endpoint URL is required in production mode for " + channelCode + " " + operation);
            }
            GateWayResponse accepted = response(channelCode, displayName, operation, request, 202, "SUBMITTED", "Sandbox endpoint not configured");
            record(channelCode, operation, request, endpointKey, 202, "SANDBOX_ACCEPTED", accepted.getMessage());
            return accepted;
        }
        String body = body(channelCode, operation, request);
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(endpointUrl).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("X-CPay-Channel", channelCode);
            connection.setRequestProperty("X-CPay-Reference", request.getReference());
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
        String sql = "INSERT INTO provider_endpoint_runs (channel_code, operation_name, reference_value, endpoint_url, http_status, request_hash, response_summary, run_status) VALUES (:channel_code, :operation_name, :reference_value, :endpoint_url, :http_status, :request_hash, :response_summary, :run_status)";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("channel_code", channelCode);
        p.addValue("operation_name", operation);
        p.addValue("reference_value", request.getReference());
        p.addValue("endpoint_url", endpointUrl);
        p.addValue("http_status", httpStatus);
        p.addValue("request_hash", safeHash(channelCode + ":" + operation + ":" + request.getReference()));
        p.addValue("response_summary", trim(message));
        p.addValue("run_status", status);
        try { jdbcTemplate.update(sql, p); } catch (Exception ignored) { }
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

