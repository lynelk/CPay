package net.citotech.cito.identity;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.citotech.cito.Common;
import net.citotech.cito.Model.HttpRequestResponse;
import net.citotech.cito.security.CanonicalRequestSigner;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * GnuGrid NIN verification adapter (S5 pilot, gated by {@code identity-gnugrid}).
 *
 * <p>Mirrors the channel-adapter pattern: with no endpoint configured the connector resolves
 * requests through deterministic sandbox scenarios (NINs ending {@code 00011} fail, all others
 * verify); in production the request is POSTed to {@code {baseUrl}/v1/verifications} through {@link
 * Common#doHttpRequest} (same shared outbound executor as every provider call, so TLS, timeouts,
 * correlation-id propagation, and Micrometer metrics apply). Provider responses are read with
 * org.json like the rest of the legacy provider code.
 *
 * <p>No raw PII ever leaves this class in an error message: failures report the HTTP status and a
 * generic reason only.
 */
@Component
public class GnuGridConnector implements IdentityVerificationConnector {

    public static final String PROVIDER_CODE = "gnugrid";
    static final String ENDPOINT_PATH = "/v1/verifications";
    static final String SANDBOX_FAIL_SUFFIX = "00011";

    private final String baseUrl;
    private final String apiKey;

    public GnuGridConnector(
            @Value("${cpay.identity.gnugrid.base-url:}") String baseUrl,
            @Value("${cpay.identity.gnugrid.api-key:}") String apiKey) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public boolean supportsSync() {
        return true;
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    @Override
    public IdentityRecords.VerifiedIdentity verify(
            IdentityRecords.IdentityVerificationRequest request) {
        String ref = request.requestReference();
        if (baseUrl.isEmpty()) {
            return sandbox(request);
        }
        String url = baseUrl.replaceAll("/+$", "") + ENDPOINT_PATH;
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-CPay-Reference", ref);
        if (!apiKey.isEmpty()) {
            headers.put("Authorization", "Bearer " + apiKey);
        }
        try {
            HttpRequestResponse httpResponse =
                    Common.doHttpRequest("POST", url, requestBody(request), headers);
            int status = httpResponse.getStatusCode();
            String body = httpResponse.getResponse() == null ? "" : httpResponse.getResponse();
            if (status >= 200 && status < 300 && !body.isBlank()) {
                return parseResponse(ref, body);
            }
            String reason =
                    status == 0
                            ? "provider unreachable: "
                                    + safeProviderMessage(httpResponse.getErrorMessage())
                            : "provider rejected the request (HTTP " + status + ")";
            throw new IdentityVerificationException(reason);
        } catch (IdentityVerificationException e) {
            throw e;
        } catch (Exception e) {
            throw new IdentityVerificationException(
                    "identity verification failed: " + safeProviderMessage(e.getMessage()));
        }
    }

    @Override
    public IdentityRecords.VerifiedIdentity parseCallback(
            String callbackBody, Map<String, String> callbackHeaders) {
        if (callbackBody == null || callbackBody.isBlank()) {
            throw new IdentityVerificationException("identity callback body is empty");
        }
        try {
            JSONObject json = new JSONObject(callbackBody);
            String ref = json.optString("reference", "");
            return parseResponse(ref, callbackBody);
        } catch (Exception e) {
            throw new IdentityVerificationException("identity callback payload is unreadable");
        }
    }

    @Override
    public boolean validateCallbackHeaders(Map<String, String> callbackHeaders) {
        // No signing secret configured for this provider yet; no verifiable material to check
        // against (mirrors PaymentChannelAdapter#verifyCallback's permissive default).
        return true;
    }

    private IdentityRecords.VerifiedIdentity sandbox(
            IdentityRecords.IdentityVerificationRequest request) {
        String nin = request.nin().trim();
        String ref = request.requestReference();
        if (nin.endsWith(SANDBOX_FAIL_SUFFIX)) {
            return IdentityRecords.VerifiedIdentity.failed(
                    ref, PROVIDER_CODE, "sandbox-" + ref, "SANDBOX_NOT_VERIFIED");
        }
        return IdentityRecords.VerifiedIdentity.matched(
                ref,
                PROVIDER_CODE,
                "sandbox-" + ref,
                maskName(request.fullName()),
                Instant.now(),
                Instant.now().plusSeconds(30L * 24L * 60L * 60L),
                "SANDBOX_VERIFIED");
    }

    private IdentityRecords.VerifiedIdentity parseResponse(String ref, String body) {
        JSONObject json = new JSONObject(body);
        boolean verified = json.optBoolean("verified", false);
        String providerReference = json.optString("providerReference", "");
        if (!verified) {
            return IdentityRecords.VerifiedIdentity.failed(
                    ref, PROVIDER_CODE, providerReference, body);
        }
        String fullName =
                firstNonBlank(
                        json.optString("fullName", ""),
                        json.optString("firstName", ""),
                        json.optString("lastName", ""));
        String expires = json.optString("expiresAt", "");
        return IdentityRecords.VerifiedIdentity.matched(
                ref,
                PROVIDER_CODE,
                providerReference,
                maskName(fullName),
                Instant.now(),
                parseExpiry(expires),
                body);
    }

    private String requestBody(IdentityRecords.IdentityVerificationRequest request) {
        StringBuilder body = new StringBuilder();
        body.append("{\"reference\":\"")
                .append(esc(request.requestReference()))
                .append("\",\"nin\":\"")
                .append(esc(request.nin().trim()))
                .append("\",\"fullName\":\"")
                .append(esc(request.fullName()))
                .append("\",\"msisdn\":\"")
                .append(esc(request.msisdn()))
                .append("\"}");
        return body.toString();
    }

    private Instant parseExpiry(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String safeProviderMessage(String message) {
        if (message == null || message.isBlank()) {
            return "no provider detail";
        }
        return message.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ._-]", "");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String esc(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static String maskName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "";
        }
        String trimmed = fullName.trim();
        if (trimmed.length() <= 2) {
            return trimmed.charAt(0) + "*";
        }
        return trimmed.substring(0, 1)
                + "*".repeat(trimmed.length() - 2)
                + trimmed.substring(trimmed.length() - 1);
    }

    static String maskMsisdn(String msisdn) {
        if (msisdn == null || msisdn.isBlank()) {
            return "";
        }
        String digits = msisdn.replaceAll("[^0-9]", "");
        if (digits.length() <= 5) {
            return "*".repeat(Math.max(1, digits.length()));
        }
        return digits.substring(0, 3)
                + "*".repeat(digits.length() - 5)
                + digits.substring(digits.length() - 2);
    }

    static String sha256Hex(String value) {
        return CanonicalRequestSigner.sha256Hex(
                value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
    }
}
