package net.citotech.cito.gateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import net.citotech.cito.Common;
import net.citotech.cito.Model.GateWayResponse;
import net.citotech.cito.Model.HttpRequestResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProviderEndpointExecutionService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ProviderTokenStoreService tokenStoreService;
    private final ChannelCircuitBreaker circuitBreaker;

    public ProviderEndpointExecutionService(
            NamedParameterJdbcTemplate jdbcTemplate,
            ProviderTokenStoreService tokenStoreService,
            ChannelCircuitBreaker circuitBreaker) {
        this.jdbcTemplate = jdbcTemplate;
        this.tokenStoreService = tokenStoreService;
        this.circuitBreaker = circuitBreaker;
    }

    public GateWayResponse execute(
            String channelCode,
            String displayName,
            String operation,
            PaymentGatewayRequest request) {
        String endpointKey = operation.toLowerCase() + "Url";
        String endpointUrl = request.getMetadata().get(endpointKey);
        String gatewayState = request.getMetadata().getOrDefault("gatewayState", "SANDBOX");
        if (isBlank(endpointUrl)) {
            if ("PRODUCTION".equalsIgnoreCase(gatewayState)) {
                throw new PaymentGatewayException(
                        "Provider endpoint URL is required in production mode for "
                                + channelCode
                                + " "
                                + operation);
            }
            SandboxScenario scenario = sandboxScenario(operation, request.getAccountIdentifier());
            GateWayResponse accepted =
                    response(
                            channelCode,
                            displayName,
                            operation,
                            request,
                            scenario.httpStatus,
                            scenario.transactionStatus,
                            scenario.message);
            record(
                    channelCode,
                    operation,
                    request,
                    endpointKey,
                    scenario.httpStatus,
                    scenario.runStatus,
                    accepted.getMessage());
            return accepted;
        }

        // Per-channel circuit breaker (audit B5): a provider outage previously meant every
        // request paid the full connect/read timeout one at a time. Once enough consecutive
        // failures accrue, short-circuit further calls to this channel for a cooldown window
        // instead of hammering a known-down provider.
        if (!circuitBreaker.allowRequest(channelCode)) {
            String message =
                    "Circuit breaker is open for "
                            + channelCode
                            + " - provider calls are temporarily suspended";
            record(channelCode, operation, request, endpointUrl, 0, "CIRCUIT_OPEN", message);
            GateWayResponse shortCircuited =
                    response(channelCode, displayName, operation, request, 0, "FAILED", message);
            shortCircuited.setRequestTrace(
                    "circuitBreakerState=" + circuitBreaker.stateOf(channelCode));
            return shortCircuited;
        }

        String body = body(channelCode, operation, request);
        try {
            Map<String, String> requestHeaders = new LinkedHashMap<>();
            requestHeaders.put("Content-Type", "application/json");
            requestHeaders.put("X-CPay-Channel", channelCode);
            requestHeaders.put("X-CPay-Reference", request.getReference());
            tokenStoreService
                    .findValid(channelCode, operation, gatewayState)
                    .ifPresent(
                            token ->
                                    requestHeaders.put(
                                            "Authorization", "Bearer " + token.getTokenValue()));
            String headerName = request.getMetadata().get("authHeaderName");
            String headerValue = request.getMetadata().get("authHeaderValue");
            if (!isBlank(headerName) && !isBlank(headerValue)) {
                requestHeaders.put(headerName, headerValue);
            }
            HttpRequestResponse httpResponse =
                    Common.doHttpRequest("POST", endpointUrl, body, requestHeaders);
            int httpStatus = httpResponse.getStatusCode();
            String responseBody = httpResponse.getResponse() == null ? "" : httpResponse.getResponse();
            String status = httpStatus >= 200 && httpStatus < 300 ? "SUBMITTED" : "FAILED";
            String runStatus = status;
            String recordMessage = responseBody;

            // Audit C9: Yo! Payments has no inbound webhook wired into CPay - collect/payout both
            // resolve through this synchronous HTTP response, and every response header used to be
            // discarded here, so a tampered/forged 2xx body would have been trusted outright.
            // Verify
            // the response signature (when the provider actually sent one) before honouring a
            // "success" status. Scoped to yo_payments specifically: it is the only channel driven
            // by
            // this service that has a signing secret (apiKey) already sitting in its merchant
            // channel
            // credentials with no other verification in front of it. See
            // YoPaymentsCallbackVerifier.
            if ("SUBMITTED".equals(status)
                    && YoPaymentsAdapter.CHANNEL_CODE.equalsIgnoreCase(channelCode)) {
                Map<String, String> responseHeaders =
                        httpResponse.getResponseHeaders() == null
                                ? Map.of()
                                : httpResponse.getResponseHeaders();
                if (!YoPaymentsCallbackVerifier.verify(
                        responseHeaders, responseBody, request.getMetadata())) {
                    status = "FAILED";
                    runStatus = "SIGNATURE_INVALID";
                    recordMessage =
                            "Provider response signature verification failed - response rejected as untrusted";
                }
            }

            if ("SUBMITTED".equals(status)) {
                circuitBreaker.recordSuccess(channelCode);
            } else {
                circuitBreaker.recordFailure(channelCode);
            }
            record(
                    channelCode,
                    operation,
                    request,
                    endpointUrl,
                    httpStatus,
                    runStatus,
                    recordMessage);
            // Audit C6: on failure, `recordMessage` is the RAW, unfiltered provider response body -
            // never hand it to a merchant directly. The raw body is already preserved internally
            // either way, in the provider_endpoint_runs.response_summary row written by record()
            // just
            // above. SIGNATURE_INVALID is checked first and separately: it is not a business
            // decline
            // (see ProviderErrorTranslator#SIGNATURE_VERIFICATION_FAILED's javadoc) and must not be
            // run through the generic HTTP-status-based translation, which would otherwise conflate
            // it
            // with an ordinary provider decline and lose that distinction.
            String merchantMessage;
            if ("SIGNATURE_INVALID".equals(runStatus)) {
                merchantMessage =
                        ProviderErrorTranslator.SIGNATURE_VERIFICATION_FAILED.merchantMessage();
            } else if ("FAILED".equals(status)) {
                merchantMessage =
                        ProviderErrorTranslator.translateProviderResponse(httpStatus, responseBody)
                                .merchantMessage();
            } else {
                merchantMessage = trim(recordMessage);
            }
            GateWayResponse result =
                    response(
                            channelCode,
                            displayName,
                            operation,
                            request,
                            httpStatus,
                            status,
                            merchantMessage);
            result.setRequestTrace("requestHash=" + hash(body));
            return result;
        } catch (Exception e) {
            circuitBreaker.recordFailure(channelCode);
            record(channelCode, operation, request, endpointUrl, 0, "FAILED", e.getMessage());
            throw new PaymentGatewayException(
                    "Provider endpoint execution failed: " + e.getMessage());
        }
    }

    private GateWayResponse response(
            String channelCode,
            String displayName,
            String operation,
            PaymentGatewayRequest request,
            int httpStatus,
            String status,
            String message) {
        GateWayResponse response = new GateWayResponse();
        response.setStatus("FAILED".equals(status) ? "FAILED" : "SUCCESS");
        response.setTransactionStatus(status);
        response.setHttpStatus(String.valueOf(httpStatus));
        response.setMessage(displayName + " " + operation + " provider response: " + message);
        response.setNetworkId(channelCode + "-" + request.getReference());
        return response;
    }

    private void record(
            String channelCode,
            String operation,
            PaymentGatewayRequest request,
            String endpointUrl,
            int httpStatus,
            String status,
            String message) {
        String sql =
                "INSERT INTO provider_endpoint_runs (channel_code, operation_name, reference_value, endpoint_url, http_status, request_hash, response_summary, run_status, merchant_number, environment) VALUES (:channel_code, :operation_name, :reference_value, :endpoint_url, :http_status, :request_hash, :response_summary, :run_status, :merchant_number, :environment)";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("channel_code", channelCode);
        p.addValue("operation_name", operation);
        p.addValue("reference_value", request.getReference());
        p.addValue("endpoint_url", endpointUrl);
        p.addValue("http_status", httpStatus);
        p.addValue(
                "request_hash",
                safeHash(channelCode + ":" + operation + ":" + request.getReference()));
        p.addValue("response_summary", trim(message));
        p.addValue("run_status", status);
        p.addValue("merchant_number", request.getMerchantNumber());
        p.addValue(
                "environment",
                request.getMetadata()
                        .getOrDefault(
                                "credentialEnvironment",
                                request.getMetadata().getOrDefault("gatewayState", "SANDBOX")));
        try {
            jdbcTemplate.update(sql, p);
        } catch (Exception ignored) {
        }
    }

    private SandboxScenario sandboxScenario(String operation, String accountIdentifier) {
        String account = accountIdentifier == null ? "" : accountIdentifier.trim();
        String op = operation == null ? "" : operation.trim().toUpperCase();
        if ("COLLECT".equals(op)) {
            if (account.endsWith("000002"))
                return new SandboxScenario(
                        202,
                        "FAILED",
                        "SANDBOX_FAILED",
                        "Sandbox customer declined the collection");
            if (account.endsWith("000003"))
                return new SandboxScenario(
                        202, "PENDING", "SANDBOX_PENDING", "Sandbox collection remains pending");
            if (account.endsWith("000004"))
                return new SandboxScenario(
                        202, "UNKNOWN", "SANDBOX_TIMEOUT", "Sandbox provider timeout scenario");
            if (account.endsWith("000005"))
                return new SandboxScenario(
                        400, "FAILED", "SANDBOX_REJECTED", "Sandbox account is unsupported");
            return new SandboxScenario(
                    202,
                    "SUCCESSFUL",
                    "SANDBOX_ACCEPTED",
                    "Sandbox collection accepted and resolved successfully");
        }
        if ("PAYOUT".equals(op)) {
            if (account.endsWith("000002"))
                return new SandboxScenario(
                        202, "FAILED", "SANDBOX_FAILED", "Sandbox payout failed");
            return new SandboxScenario(
                    202,
                    "SUCCESSFUL",
                    "SANDBOX_ACCEPTED",
                    "Sandbox payout accepted and resolved successfully");
        }
        return new SandboxScenario(
                202, "SUBMITTED", "SANDBOX_ACCEPTED", "Sandbox endpoint not configured");
    }

    private static class SandboxScenario {
        private final int httpStatus;
        private final String transactionStatus;
        private final String runStatus;
        private final String message;

        private SandboxScenario(
                int httpStatus, String transactionStatus, String runStatus, String message) {
            this.httpStatus = httpStatus;
            this.transactionStatus = transactionStatus;
            this.runStatus = runStatus;
            this.message = message;
        }
    }

    private String body(String channelCode, String operation, PaymentGatewayRequest request) {
        return "{\"channelCode\":\""
                + esc(channelCode)
                + "\",\"operation\":\""
                + esc(operation)
                + "\",\"merchantNumber\":\""
                + esc(request.getMerchantNumber())
                + "\",\"accountIdentifier\":\""
                + esc(request.getAccountIdentifier())
                + "\",\"amount\":"
                + request.getAmount()
                + ",\"reference\":\""
                + esc(request.getReference())
                + "\",\"description\":\""
                + esc(request.getDescription())
                + "\",\"callbackUrl\":\""
                + esc(request.getCallbackUrl())
                + "\"}";
    }

    private String hash(String value) throws Exception {
        return safeHash(value);
    }

    private String safeHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes =
                    digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) builder.append(String.format("%02x", b));
            return builder.toString();
        } catch (Exception e) {
            return "hash-error";
        }
    }

    private String trim(String value) {
        return value == null ? "" : (value.length() <= 1000 ? value : value.substring(0, 1000));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String esc(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
