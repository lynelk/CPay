package net.citotech.cito.identity.provider.gnugrid;

import java.util.LinkedHashMap;
import java.util.Map;
import net.citotech.cito.Common;
import net.citotech.cito.Model.HttpRequestResponse;
import net.citotech.cito.identity.provider.ValidationProviderException;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

/**
 * gnuGrid CRB ID-validation client (ISO domain mapping: identity/provider/gnugrid). POSTs NIN /
 * personal-information checks to the approved ID Validation endpoint configured via
 * {@code cpay.identity.gnugrid.id-validation-path} (pilot-compatible default
 * {@code /v1/verifications}). The bearer token comes from {@link GnuGridTokenManager}; provider
 * outcomes are normalized into CPay codes — {@code NO_MATCH}/{@code MISMATCH} are business
 * evidence, {@code AUTHENTICATION_PROBLEM}/{@code CONNECTION_PROBLEM} are technical errors.
 */
@Component
public class GnuGridIdValidationClient {

    /** Deterministic sandbox fixture (matches the S5 pilot): NINs ending {@code 00011} fail. */
    static final String SANDBOX_FAIL_SUFFIX = "00011";

    private final GnuGridProperties properties;
    private final GnuGridTokenManager tokenManager;

    public GnuGridIdValidationClient(
            GnuGridProperties properties, GnuGridTokenManager tokenManager) {
        this.properties = properties;
        this.tokenManager = tokenManager;
    }

    /**
     * Executes a NIN / personal-information validation. {@code attributes} carries the provider
     * request fields (e.g. {@code nin}, {@code fullName}, {@code msisdn}, {@code reference}).
     */
    public ProviderIdValidationResult validate(String reference, Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            throw new ValidationProviderException(
                    GnuGridTokenManager.PROVIDER_CODE,
                    "SUBJECT_DATA_INVALID",
                    "gnuGrid ID validation requires subject attributes");
        }
        String baseUrl = properties.baseUrl();
        if (baseUrl.isEmpty()) {
            return sandbox(reference, attributes);
        }
        String url = baseUrl.replaceAll("/+$", "") + properties.idValidationPath();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + tokenManager.accessToken());
        headers.put("X-CPay-Reference", reference);
        try {
            HttpRequestResponse http =
                    Common.doHttpRequest("POST", url, requestBody(reference, attributes), headers);
            int status = http.getStatusCode();
            String body = http.getResponse() == null ? "" : http.getResponse();
            if (status >= 200 && status < 300 && !body.isBlank()) {
                return normalize(reference, body);
            }
            if (status == 401) {
                throw new ValidationProviderException(
                        GnuGridTokenManager.PROVIDER_CODE,
                        "PROVIDER_AUTHENTICATION_ERROR",
                        "gnuGrid ID validation rejected the access token (HTTP 401)");
            }
            throw new ValidationProviderException(
                    GnuGridTokenManager.PROVIDER_CODE,
                    status == 0 || status >= 500
                            ? "PROVIDER_TEMPORARILY_UNAVAILABLE"
                            : "VALIDATION_REQUEST_INVALID",
                    "gnuGrid ID validation failed (HTTP " + status + ")");
        } catch (ValidationProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationProviderException(
                    GnuGridTokenManager.PROVIDER_CODE,
                    "PROVIDER_TEMPORARILY_UNAVAILABLE",
                    "gnuGrid ID validation request failed");
        }
    }

    private ProviderIdValidationResult normalize(String reference, String body) {
        try {
            JSONObject json = new JSONObject(body);
            String providerReference = json.optString("providerReference", "");
            boolean verified = json.optBoolean("verified", false);
            if (!verified) {
                return new ProviderIdValidationResult(
                        false, "IDENTITY_NOT_FOUND", providerReference, Map.of());
            }
            Map<String, String> attributes = new LinkedHashMap<>();
            putIfPresent(attributes, "firstName", json.optString("firstName", ""));
            putIfPresent(attributes, "lastName", json.optString("lastName", ""));
            putIfPresent(attributes, "fullName", json.optString("fullName", ""));
            putIfPresent(attributes, "expiresAt", json.optString("expiresAt", ""));
            return new ProviderIdValidationResult(true, "NIN_MATCH", providerReference, attributes);
        } catch (JSONException e) {
            throw new ValidationProviderException(
                    GnuGridTokenManager.PROVIDER_CODE,
                    "PROVIDER_INCONCLUSIVE",
                    "gnuGrid ID validation response was unreadable");
        }
    }

    private ProviderIdValidationResult sandbox(String reference, Map<String, String> attributes) {
        String nin = attributes.getOrDefault("nin", "");
        String providerReference = "sandbox-" + reference;
        if (nin.endsWith(SANDBOX_FAIL_SUFFIX)) {
            return new ProviderIdValidationResult(
                    false, "SANDBOX_NOT_VERIFIED", providerReference, Map.of());
        }
        return new ProviderIdValidationResult(
                true,
                "SANDBOX_VERIFIED",
                providerReference,
                Map.of("fullName", attributes.getOrDefault("fullName", "")));
    }

    private String requestBody(String reference, Map<String, String> attributes) {
        StringBuilder body = new StringBuilder();
        body.append("{\"reference\":\"")
                .append(esc(reference))
                .append("\",\"nin\":\"")
                .append(esc(attributes.getOrDefault("nin", "")))
                .append("\",\"fullName\":\"")
                .append(esc(attributes.getOrDefault("fullName", "")))
                .append("\",\"msisdn\":\"")
                .append(esc(attributes.getOrDefault("msisdn", "")))
                .append("\"}");
        return body.toString();
    }

    private String esc(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void putIfPresent(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value.trim());
        }
    }

    /**
     * Normalized ID-validation outcome. Provider responses are reduced to evidence; CPay's policy
     * engine owns the final verification decision.
     */
    public record ProviderIdValidationResult(
            boolean match, String normalizedCode, String providerReference, Map<String, String> attributes) {}
}
