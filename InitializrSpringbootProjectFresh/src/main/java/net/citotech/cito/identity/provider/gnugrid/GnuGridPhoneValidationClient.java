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
 * gnuGrid CRB phone (subscriber/ownership) validation client (ISO domain mapping:
 * identity/provider/gnugrid). POSTs MSISDN ownership checks to the approved phone validation
 * endpoint configured via {@code cpay.identity.gnugrid.phone-validation-path} (default
 * {@code /v1/verifications/phone}). This is a distinct capability from CPay OTP possession
 * ({@code PHONE_POSSESSION}): a successful response here asserts subscriber-registration evidence,
 * not that the user currently controls the handset.
 */
@Component
public class GnuGridPhoneValidationClient {

    /** Deterministic sandbox fixture: MSISDNs ending {@code 00011} fail ownership. */
    static final String SANDBOX_FAIL_SUFFIX = "00011";

    private final GnuGridProperties properties;
    private final GnuGridTokenManager tokenManager;

    public GnuGridPhoneValidationClient(
            GnuGridProperties properties, GnuGridTokenManager tokenManager) {
        this.properties = properties;
        this.tokenManager = tokenManager;
    }

    /**
     * Executes a phone (subscriber/ownership) validation. {@code attributes} carries
     * {@code msisdn}, {@code nin}, and {@code fullName} (the last two may be empty for
     * subscriber-only lookups where the contract permits).
     */
    public ProviderPhoneValidationResult validate(
            String reference, Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            throw new ValidationProviderException(
                    GnuGridTokenManager.PROVIDER_CODE,
                    "SUBJECT_DATA_INVALID",
                    "gnuGrid phone validation requires subject attributes");
        }
        String baseUrl = properties.baseUrl();
        if (baseUrl.isEmpty()) {
            return sandbox(reference, attributes);
        }
        String url = baseUrl.replaceAll("/+$", "") + properties.phoneValidationPath();
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
                        "gnuGrid phone validation rejected the access token (HTTP 401)");
            }
            throw new ValidationProviderException(
                    GnuGridTokenManager.PROVIDER_CODE,
                    status == 0 || status >= 500
                            ? "PROVIDER_TEMPORARILY_UNAVAILABLE"
                            : "VALIDATION_REQUEST_INVALID",
                    "gnuGrid phone validation failed (HTTP " + status + ")");
        } catch (ValidationProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationProviderException(
                    GnuGridTokenManager.PROVIDER_CODE,
                    "PROVIDER_TEMPORARILY_UNAVAILABLE",
                    "gnuGrid phone validation request failed");
        }
    }

    private ProviderPhoneValidationResult normalize(String reference, String body) {
        try {
            JSONObject json = new JSONObject(body);
            String providerReference = json.optString("providerReference", "");
            boolean matched = json.optBoolean("matched", json.optBoolean("verified", false));
            if (!matched) {
                return new ProviderPhoneValidationResult(
                        false, "PHONE_OWNERSHIP_NOT_MATCHED", providerReference, Map.of());
            }
            Map<String, String> attributes = new LinkedHashMap<>();
            putIfPresent(attributes, "networkName", json.optString("networkName", ""));
            putIfPresent(attributes, "networkCode", json.optString("networkCode", ""));
            putIfPresent(attributes, "subscriberNameMatch", json.optString("subscriberNameMatch", ""));
            putIfPresent(attributes, "expiresAt", json.optString("expiresAt", ""));
            return new ProviderPhoneValidationResult(
                    true, "PHONE_OWNERSHIP_MATCHED", providerReference, attributes);
        } catch (JSONException e) {
            throw new ValidationProviderException(
                    GnuGridTokenManager.PROVIDER_CODE,
                    "PROVIDER_INCONCLUSIVE",
                    "gnuGrid phone validation response was unreadable");
        }
    }

    private ProviderPhoneValidationResult sandbox(
            String reference, Map<String, String> attributes) {
        String msisdn = attributes.getOrDefault("msisdn", "");
        String providerReference = "sandbox-" + reference;
        if (msisdn.endsWith(SANDBOX_FAIL_SUFFIX)) {
            return new ProviderPhoneValidationResult(
                    false, "SANDBOX_PHONE_NOT_VERIFIED", providerReference, Map.of());
        }
        return new ProviderPhoneValidationResult(
                true,
                "SANDBOX_PHONE_VERIFIED",
                providerReference,
                Map.of("networkCode", "SANDBOX"));
    }

    private String requestBody(String reference, Map<String, String> attributes) {
        StringBuilder body = new StringBuilder();
        body.append("{\"reference\":\"")
                .append(esc(reference))
                .append("\",\"msisdn\":\"")
                .append(esc(attributes.getOrDefault("msisdn", "")))
                .append("\",\"nin\":\"")
                .append(esc(attributes.getOrDefault("nin", "")))
                .append("\",\"fullName\":\"")
                .append(esc(attributes.getOrDefault("fullName", "")))
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

    /** Normalized phone-validation outcome — provider evidence, not a CPay decision. */
    public record ProviderPhoneValidationResult(
            boolean matched,
            String normalizedCode,
            String providerReference,
            Map<String, String> attributes) {}
}
